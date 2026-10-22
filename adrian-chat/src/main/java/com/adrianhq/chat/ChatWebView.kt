package com.adrianhq.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/**
 * The chat widget in a WebView, kept alive between openings so it can count replies while closed.
 * It is created on the application context and moved into [AdrianMessengerActivity] while the chat is open.
 */
@SuppressLint("SetJavaScriptEnabled")
internal class ChatWebView(
    context: Context,
    workspaceId: String,
    widgetUrl: String,
    private val onMessage: (JSONObject) -> Unit,
) {
    private val contextWrapper = MutableContextWrapper(context)
    private val origin = Uri.parse(widgetUrl).let { "${it.scheme}://${it.authority}" }
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var resetWhenReady = false

    val webView = WebView(contextWrapper)

    init {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = true
        }
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()

        // The listener only answers pages on the widget's origin; older WebViews fall back to a JS interface.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "AdrianAndroid", setOf(origin)) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) message.data?.let(::receive)
            }
        } else {
            webView.addJavascriptInterface(Bridge(), "AdrianAndroid")
        }

        val url = Uri.parse(widgetUrl).buildUpon()
            .path("/")
            .appendQueryParameter("workspaceId", workspaceId)
            .appendQueryParameter("sdk", "android")
            .build()
        webView.loadUrl(url.toString())
    }

    /** Posts [message] to the widget the way loader.js does. Dropped until the widget says it is ready;
     *  [Adrian] re-sends the current state then. */
    fun send(message: JSONObject) {
        if (!ready) return
        message.put("source", "adrian-loader")
        webView.evaluateJavascript("window.postMessage($message, '*');", null)
    }

    fun reset() {
        if (!ready) {
            resetWhenReady = true
            return
        }
        send(JSONObject().put("type", "reset"))
        ready = false
    }

    fun attach(activityContext: Context, parent: ViewGroup) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        contextWrapper.baseContext = activityContext
        parent.addView(webView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun detach(applicationContext: Context) {
        (webView.parent as? ViewGroup)?.removeView(webView)
        contextWrapper.baseContext = applicationContext
    }

    fun destroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    private fun receive(text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        if (message.optString("source") != "adrian-widget") return
        if (message.optString("type") == "ready") {
            ready = true
            if (resetWhenReady) {
                resetWhenReady = false
                reset()
                return
            }
        }
        onMessage(message)
    }

    private fun isWidget(uri: Uri) = "${uri.scheme}://${uri.authority}" == origin

    private inner class Bridge {
        @JavascriptInterface
        fun postMessage(text: String) {
            main.post { receive(text) }
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            // Embedded videos load in sub-frames; only the chat itself may use the main frame.
            if (!request.isForMainFrame || isWidget(request.url)) return false
            Adrian.openExternal(request.url.toString())
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            ready = false
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view === webView) main.post { Adrian.recreateChat() }
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
            val messenger = Adrian.messenger ?: return false
            return messenger.chooseFiles(callback, params)
        }
    }
}
