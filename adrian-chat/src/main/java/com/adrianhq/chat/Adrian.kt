package com.adrianhq.chat

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.lang.ref.WeakReference

/** The newest message that arrived while the chat was closed, for an in-app banner. */
data class AdrianMessagePreview(val id: String, val author: String, val body: String)

/** Override what you need; every callback runs on the main thread. */
interface AdrianListener {
    /** The chat finished loading and can take `identify` and `trackScreen` calls. */
    fun onReady() {}
    fun onOpened() {}
    fun onClosed() {}
    /** Unread replies, including a greeting waiting to be seen. Zero while the chat is open. */
    fun onUnreadCountChanged(count: Int) {}
    /** A reply or proactive message arrived while the chat was closed. */
    fun onMessageReceived(message: AdrianMessagePreview) {}
    fun onPrimaryColorChanged(color: Int) {}
    /** The workspace refused this app (for example, chat is switched off). */
    fun onUnavailable(reason: String) {}
}

/**
 * Adrian live chat for Android.
 *
 * ```kotlin
 * Adrian.configure(applicationContext, workspaceId = "ws_…")
 * Adrian.identify(name = "Ada Obi", email = "ada@example.com")
 * Adrian.present(activity)
 * ```
 *
 * The conversation runs in Adrian's chat widget inside a WebView, so forms, action buttons,
 * chat flows, files and ratings work exactly as they do on your website. Call everything on the main thread.
 */
object Adrian {
    const val DEFAULT_WIDGET_URL = "https://chat.adrianhq.co"
    private const val TAG = "Adrian"

    /** Unread replies, including a greeting waiting to be seen. Zero while the chat is open. */
    var unreadCount = 0
        private set

    /** The workspace's brand colour from Settings → Chat widget. */
    var primaryColor = Color.parseColor("#2554EB")
        private set

    var latestMessage: AdrianMessagePreview? = null
        private set

    /** False when the workspace turned chat off for apps; `present()` then does nothing. */
    var isAvailable = true
        private set

    val isPresented: Boolean get() = messenger != null

    private val listeners = linkedSetOf<AdrianListener>()
    private var appContext: Context? = null
    private var workspaceId: String? = null
    private var widgetUrl: String? = null
    private var openOnProactiveMessage = false
    // Kept for the app's lifetime so replies arrive while the chat is closed; it only holds the
    // messenger activity while attached and goes back to the application context on detach.
    @SuppressLint("StaticFieldLeak")
    internal var chat: ChatWebView? = null
        private set
    private var messengerRef: WeakReference<AdrianMessengerActivity>? = null
    internal val messenger: AdrianMessengerActivity? get() = messengerRef?.get()
    private var identity: Pair<String?, String?>? = null
    private var screen: String? = null
    private var unread = 0

    /**
     * Starts the chat in the background so replies can raise [unreadCount] before the user opens it.
     * Call once, early (for example in `Application.onCreate`).
     *
     * @param workspaceId Your workspace id, from Settings → Chat widget → Install.
     * @param widgetUrl Where the chat widget is hosted. Only change this for a self-hosted Adrian.
     * @param openOnProactiveMessage Open the chat by itself when your team or a trigger starts a conversation.
     */
    @JvmStatic
    @JvmOverloads
    fun configure(context: Context, workspaceId: String, widgetUrl: String = DEFAULT_WIDGET_URL, openOnProactiveMessage: Boolean = false) {
        checkMainThread()
        val id = workspaceId.trim()
        if (id.isEmpty()) {
            Log.w(TAG, "configure() needs your workspace id")
            return
        }
        this.openOnProactiveMessage = openOnProactiveMessage
        if (id == this.workspaceId && widgetUrl == this.widgetUrl) return
        chat?.destroy()
        appContext = context.applicationContext
        this.workspaceId = id
        this.widgetUrl = widgetUrl.trimEnd('/')
        isAvailable = true
        chat = ChatWebView(context.applicationContext, id, this.widgetUrl!!, ::handle)
    }

    /** Tells your team who they are talking to. Call after sign-in; values the user typed in the chat are replaced. */
    @JvmStatic
    @JvmOverloads
    fun identify(name: String? = null, email: String? = null) {
        checkMainThread()
        identity = name?.trim()?.ifEmpty { null } to email?.trim()?.ifEmpty { null }
        sendIdentity()
    }

    /**
     * Records the screen the user is on, shown to agents like a page view (`app://<package>/<screen>`).
     * Screen-based triggers from Settings → Chat widget → Triggers fire on these.
     */
    @JvmStatic
    fun trackScreen(name: String) {
        checkMainThread()
        screen = name.trim().ifEmpty { return }
        sendScreen()
    }

    /** Opens the chat full screen. */
    @JvmStatic
    fun present(context: Context) {
        checkMainThread()
        if (chat == null) {
            Log.w(TAG, "Call Adrian.configure() before present()")
            return
        }
        if (!isAvailable || messenger != null) return
        val intent = Intent(context, AdrianMessengerActivity::class.java)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @JvmStatic
    fun dismiss() {
        messenger?.finish()
    }

    /** Call when the user signs out: the next chat starts as a new visitor with no history. */
    @JvmStatic
    fun reset() {
        checkMainThread()
        identity = null
        screen = null
        latestMessage = null
        setUnread(0)
        chat?.reset()
    }

    @JvmStatic
    fun addListener(listener: AdrianListener) {
        listeners += listener
    }

    @JvmStatic
    fun removeListener(listener: AdrianListener) {
        listeners -= listener
    }

    internal fun messengerOpened(activity: AdrianMessengerActivity) {
        messengerRef = WeakReference(activity)
        latestMessage = null
        setUnread(0)
        chat?.send(JSONObject().put("type", "open"))
        listeners.toList().forEach { it.onOpened() }
    }

    internal fun messengerClosed(activity: AdrianMessengerActivity) {
        if (messenger !== activity) return
        messengerRef = null
        chat?.send(JSONObject().put("type", "closed"))
        listeners.toList().forEach { it.onClosed() }
    }

    /** The WebView's renderer died; start a fresh one so the next opening works. */
    internal fun recreateChat() {
        val context = appContext ?: return
        val id = workspaceId ?: return
        val url = widgetUrl ?: return
        chat?.destroy()
        chat = ChatWebView(context, id, url, ::handle)
        messenger?.finish()
    }

    private fun sendIdentity() {
        val (name, email) = identity ?: return
        chat?.send(JSONObject().put("type", "identify").putOpt("name", name).putOpt("email", email))
    }

    private fun sendScreen() {
        val name = screen ?: return
        val slug = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "screen" }
        val app = appContext?.packageName ?: "app"
        val page = JSONObject().put("url", "app://$app/$slug").put("title", name).put("referrer", "")
        chat?.send(JSONObject().put("type", "page").put("page", page))
    }

    private fun setUnread(count: Int) {
        unread = count
        val shown = if (isPresented) 0 else maxOf(count, if (latestMessage == null) 0 else 1)
        if (shown == unreadCount) return
        unreadCount = shown
        listeners.toList().forEach { it.onUnreadCountChanged(shown) }
    }

    private fun handle(message: JSONObject) {
        when (message.optString("type")) {
            "ready" -> {
                chat?.send(JSONObject().put("type", if (isPresented) "open" else "closed"))
                sendIdentity()
                sendScreen()
                listeners.toList().forEach { it.onReady() }
            }
            "close" -> dismiss()
            "open" -> if (!isPresented) {
                val context = appContext
                if (openOnProactiveMessage && context != null) present(context) else setUnread(maxOf(unread, 1))
            }
            "unread" -> setUnread(message.optInt("count"))
            "teaser" -> {
                val teaser = message.optJSONObject("teaser")
                val id = teaser?.optString("id").orEmpty()
                val body = teaser?.optString("body").orEmpty()
                if (teaser != null && id.isNotEmpty() && body.isNotEmpty() && !isPresented) {
                    val preview = AdrianMessagePreview(id, teaser.optString("author"), body)
                    latestMessage = preview
                    listeners.toList().forEach { it.onMessageReceived(preview) }
                } else {
                    latestMessage = null
                }
                setUnread(unread)
            }
            "settings" -> {
                val hex = message.optJSONObject("appearance")?.optString("primaryColor").orEmpty()
                if (Regex("^#[0-9a-fA-F]{6}$").matches(hex)) {
                    primaryColor = Color.parseColor(hex)
                    listeners.toList().forEach { it.onPrimaryColorChanged(primaryColor) }
                }
            }
            "disabled" -> {
                val reason = message.optString("message").ifEmpty { "Chat is not enabled for this app." }
                Log.w(TAG, reason)
                isAvailable = false
                dismiss()
                listeners.toList().forEach { it.onUnavailable(reason) }
            }
            "link" -> openExternal(message.optString("url"))
        }
    }

    internal fun openExternal(url: String) {
        val context = messenger ?: appContext ?: return
        val uri = Uri.parse(url)
        if (uri.scheme !in setOf("http", "https", "mailto", "tel")) return
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Log.w(TAG, "No app can open $url")
        }
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Call Adrian from the main thread" }
    }
}
