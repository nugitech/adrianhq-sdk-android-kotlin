package com.adrianhq.example

import android.app.Application
import com.adrianhq.chat.Adrian

class ExampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Settings → Chat widget → Install in Adrian.
        Adrian.configure(this, workspaceId = BuildConfig.ADRIAN_WORKSPACE_ID, widgetUrl = BuildConfig.ADRIAN_WIDGET_URL)
    }
}
