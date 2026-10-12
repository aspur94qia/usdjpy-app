package com.usdjpy.analisa

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var w: WebView

    inner class Bridge {
        @JavascriptInterface
        fun get(id: String, u: String) {
            Thread {
                val r = try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    c.connectTimeout = 10000
                    c.readTimeout = 15000
                    c.setRequestProperty("User-Agent", "Mozilla/5.0")
                    c.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    "ERR:" + e.message
                }
                val q = org.json.JSONObject.quote(r)
                runOnUiThread { w.evaluateJavascript("window.onNet('$id',$q)", null) }
            }.start()
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.addJavascriptInterface(Bridge(), "Android")
        w.webViewClient = WebViewClient()
        setContentView(w)
        w.loadUrl("file:///android_asset/index.html")
    }
}
