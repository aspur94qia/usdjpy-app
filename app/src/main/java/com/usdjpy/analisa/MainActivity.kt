package com.usdjpy.analisa

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : Activity() {
    private lateinit var w: WebView
    private lateinit var a: WebView
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private var trToken = 0

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
                val q = JSONObject.quote(r)
                runOnUiThread { w.evaluateJavascript("window.onNet('$id',$q)", null) }
            }.start()
        }

        @JavascriptInterface
        fun trans(id: String, t: String) {
            Thread {
                val q = JSONObject.quote(doTr(t))
                runOnUiThread { w.evaluateJavascript("window.onNet('$id',$q)", null) }
            }.start()
        }

        @JavascriptInterface
        fun openArticle(url: String) {
            if (!url.startsWith("http")) return
            runOnUiThread {
                panel.visibility = View.VISIBLE
                status.text = "Membuka berita..."
                a.loadUrl(url)
            }
        }
    }

    private fun doTr(t: String): String = try {
        val c = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=id&dt=t").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        c.outputStream.use { it.write(("q=" + URLEncoder.encode(t, "UTF-8")).toByteArray()) }
        val r = c.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONArray(r).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until arr.length()) sb.append(arr.getJSONArray(i).optString(0, ""))
        sb.toString().ifEmpty { t }
    } catch (e: Exception) {
        t
    }

    private fun translate() {
        val tok = ++trToken
        status.text = "Membaca halaman..."
        val js = "(function(){var l=[].slice.call(document.querySelectorAll('h1,h2,p,li')).filter(function(e){var t=(e.innerText||'').trim();return t.length>30&&t.length<1500&&e.querySelectorAll('p,li,h1,h2').length===0}).slice(0,60);window.__tr=l;return JSON.stringify(l.map(function(e){return e.innerText.trim()}))})()"
        a.evaluateJavascript(js) { res ->
            if (tok != trToken || res == null || res == "null") return@evaluateJavascript
            val arr = try { JSONArray(JSONTokener(res).nextValue() as String) } catch (e: Exception) { null }
            if (arr == null || arr.length() == 0) {
                status.text = "Tidak ada teks untuk diterjemahkan"
                return@evaluateJavascript
            }
            Thread {
                for (i in 0 until arr.length()) {
                    if (tok != trToken) return@Thread
                    val t = doTr(arr.getString(i))
                    runOnUiThread {
                        if (tok == trToken) {
                            a.evaluateJavascript("if(window.__tr&&window.__tr[$i])window.__tr[$i].innerText=${JSONObject.quote(t)}", null)
                            status.text = "Terjemahan Indonesia ${i + 1}/${arr.length()}"
                        }
                    }
                }
            }.start()
        }
    }

    private fun closePanel() {
        trToken++
        panel.visibility = View.GONE
        a.loadUrl("about:blank")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (panel.visibility == View.VISIBLE) {
            if (a.canGoBack()) a.goBack() else closePanel()
        } else super.onBackPressed()
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.addJavascriptInterface(Bridge(), "Android")
        w.webViewClient = WebViewClient()

        a = WebView(this)
        a.settings.javaScriptEnabled = true
        a.settings.domStorageEnabled = true
        a.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val s = r.url.scheme
                return !(s == "http" || s == "https")
            }

            override fun onPageFinished(v: WebView, u: String) {
                val h = Uri.parse(u).host ?: ""
                if (u.startsWith("http") && !h.endsWith("google.com")) {
                    a.postDelayed({ translate() }, 800)
                } else if (u.startsWith("http")) {
                    status.text = "Mengarahkan ke sumber berita..."
                }
            }
        }

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(Color.parseColor("#0e151c"))
        panel.visibility = View.GONE
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(16, 16, 16, 16)
        val back = Button(this)
        back.text = "Kembali"
        back.setOnClickListener { closePanel() }
        status = TextView(this)
        status.setTextColor(Color.parseColor("#93a1ae"))
        status.textSize = 12f
        status.setPadding(16, 0, 16, 0)
        val re = Button(this)
        re.text = "Terjemah"
        re.setOnClickListener { translate() }
        bar.addView(back)
        bar.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(re)
        panel.addView(bar)
        panel.addView(a, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val root = FrameLayout(this)
        root.addView(w)
        root.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        w.loadUrl("file:///android_asset/index.html")
    }
}
