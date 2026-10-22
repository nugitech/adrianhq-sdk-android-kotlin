package com.adrianhq.example

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.adrianhq.chat.Adrian
import com.adrianhq.chat.AdrianListener

class MainActivity : Activity() {
    private lateinit var helpBadge: TextView
    private var signedIn = false

    private val listener = object : AdrianListener {
        override fun onUnreadCountChanged(count: Int) = renderHelpBadge(count)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        helpBadge = findViewById(R.id.help_badge)
        findViewById<View>(R.id.help).setOnClickListener { Adrian.present(this) }

        val name = findViewById<TextView>(R.id.name)
        val account = findViewById<Button>(R.id.account)
        account.setOnClickListener {
            if (signedIn) Adrian.reset() else Adrian.identify(name = "Ada Obi", email = "ada@example.com")
            signedIn = !signedIn
            name.text = if (signedIn) "Ada Obi" else "Welcome"
            account.text = if (signedIn) "Sign out" else "Sign in as Ada"
        }
    }

    override fun onStart() {
        super.onStart()
        Adrian.addListener(listener)
        renderHelpBadge(Adrian.unreadCount)
        Adrian.trackScreen("Home")
    }

    override fun onStop() {
        Adrian.removeListener(listener)
        super.onStop()
    }

    private fun renderHelpBadge(count: Int) {
        helpBadge.text = "$count new"
        helpBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
    }
}
