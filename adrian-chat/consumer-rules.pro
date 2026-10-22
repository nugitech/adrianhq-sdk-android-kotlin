# The chat widget calls this bridge from JavaScript by name.
-keepclassmembers class com.adrianhq.chat.ChatWebView$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
