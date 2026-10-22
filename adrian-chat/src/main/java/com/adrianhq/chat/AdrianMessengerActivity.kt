package com.adrianhq.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** Full-screen chat. The widget draws its own header with a close button; the status bar takes the brand colour. */
class AdrianMessengerActivity : ComponentActivity() {
    private lateinit var statusBar: View
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val listener = object : AdrianListener {
        override fun onPrimaryColorChanged(color: Int) {
            statusBar.setBackgroundColor(color)
        }
    }

    private val pickFiles = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uris = when {
            result.resultCode != RESULT_OK -> null
            data?.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chat = Adrian.chat
        if (chat == null) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.WHITE

        statusBar = View(this).apply { setBackgroundColor(Adrian.primaryColor) }
        val content = FrameLayout(this)
        val bottom = View(this).apply { setBackgroundColor(Color.WHITE) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            addView(statusBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
            addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0))
        }
        // Edge to edge: the status bar strip and the bottom spacer (navigation bar or keyboard) take the insets.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, 0, bars.right, 0)
            statusBar.layoutParams = statusBar.layoutParams.apply { height = bars.top }
            bottom.layoutParams = bottom.layoutParams.apply { height = maxOf(bars.bottom, ime.bottom) }
            WindowInsetsCompat.CONSUMED
        }
        setContentView(root)
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }

        chat.attach(this, content)
        Adrian.addListener(listener)
        Adrian.messengerOpened(this)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        Adrian.removeListener(listener)
        Adrian.chat?.detach(applicationContext)
        Adrian.messengerClosed(this)
        super.onDestroy()
    }

    internal fun chooseFiles(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        val intent = params.createIntent().apply {
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return try {
            pickFiles.launch(intent)
            true
        } catch (_: ActivityNotFoundException) {
            fileCallback = null
            false
        }
    }
}
