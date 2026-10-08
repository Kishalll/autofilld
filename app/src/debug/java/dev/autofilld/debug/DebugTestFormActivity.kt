package dev.autofilld.debug

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.widget.Button
import android.widget.Toast
import dev.autofilld.R

class DebugTestFormActivity : Activity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_test_form)

        findViewById<Button>(R.id.btnSubmitNative)?.setOnClickListener {
            Toast.makeText(this, "Native form submitted", Toast.LENGTH_SHORT).show()
        }

        val webView = findViewById<WebView>(R.id.testWebView)
        webView?.apply {
            settings.javaScriptEnabled = true
            loadUrl("file:///android_asset/testform.html")
        }
    }
}
