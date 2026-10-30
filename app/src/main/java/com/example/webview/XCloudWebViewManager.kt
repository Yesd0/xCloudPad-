package com.example.webview

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.data.XCloudPadRepository
import com.example.model.GamepadState
import com.example.model.StreamEngineSettings
import com.example.model.StreamTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class XCloudWebViewManager(
    appContext: Context,
    val repository: XCloudPadRepository,
    val scope: CoroutineScope,
    private val onTelemetryUpdate: (StreamTelemetry) -> Unit,
    private val onStreamStateUpdate: (Boolean, String) -> Unit,
    private val onEngineLog: (String) -> Unit,
    private val onPageUrlChanged: (String) -> Unit
) {
    companion object {
        const val XCLOUD_PLAY_URL = "https://www.xbox.com/play"
        const val DESKTOP_CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableContext = MutableContextWrapper(appContext.applicationContext)

    private val userscriptCode: String by lazy {
        runCatching {
            appContext.assets.open("userscript/xcloud_pad_bundle.js").bufferedReader().use { it.readText() }
        }.getOrElse { err ->
            onEngineLog("Asset read error: ${err.message}")
            ""
        }
    }

    val webView: WebView = createConfiguredWebView(appContext)
    private var currentSettings: StreamEngineSettings = StreamEngineSettings()

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun createConfiguredWebView(appContext: Context): WebView {
        val wv = WebView(mutableContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            keepScreenOn = true
        }

        // Persist first-party and third-party cookies for Microsoft Sign-In
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(wv, true)

        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = DESKTOP_CHROME_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
        }

        wv.addJavascriptInterface(XCloudPadJsBridge(), "XCloudPadBridge")

        // Inject userscript at document-start using WebViewCompat.addDocumentStartJavaScript across all origins/frames
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && userscriptCode.isNotBlank()) {
                WebViewCompat.addDocumentStartJavaScript(wv, userscriptCode, setOf("*"))
                onEngineLog("WebViewCompat.addDocumentStartJavaScript registered")
            }
        } catch (t: Throwable) {
            onEngineLog("DocumentStart registration fallback: ${t.message}")
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let { onPageUrlChanged(it) }
                // Fallback idempotent injection (script checks window.__XCLOUD_PAD_INITIALIZED__)
                if (view != null && userscriptCode.isNotBlank()) {
                    val initSettingsScript =
                        "window.__XCLOUD_PAD_INITIAL_SETTINGS__ = ${repository.serializeStreamSettings(currentSettings)};"
                    view.evaluateJavascript(initSettingsScript + "\n" + userscriptCode, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { onPageUrlChanged(it) }
                if (view != null && userscriptCode.isNotBlank()) {
                    val initSettingsScript =
                        "window.__XCLOUD_PAD_INITIAL_SETTINGS__ = ${repository.serializeStreamSettings(currentSettings)};"
                    view.evaluateJavascript(initSettingsScript + "\n" + userscriptCode, null)
                }
                // Flush cookies so Microsoft login persists across app restarts
                runCatching { CookieManager.getInstance().flush() }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // Keep Microsoft OAuth (login.live.com / login.microsoftonline.com) and xbox.com inside the same WebView
                return false
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let { req ->
                    val allowed = req.resources.filter { res ->
                        res == PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                            res == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                    }.toTypedArray()
                    if (allowed.isNotEmpty()) {
                        req.grant(allowed)
                    } else {
                        req.deny()
                    }
                }
            }
        }

        wv.loadUrl(XCLOUD_PLAY_URL)
        return wv
    }

    /**
     * Attaches the single live WebView into [targetContainer], detaching it from any previous parent
     * without reloading the page or interrupting the WebRTC stream.
     */
    fun attachWebViewTo(targetContainer: FrameLayout, displayContext: Context) {
        mainHandler.post {
            mutableContext.baseContext = displayContext
            val currentParent = webView.parent as? ViewGroup
            if (currentParent !== targetContainer) {
                currentParent?.removeView(webView)
                targetContainer.removeAllViews()
                targetContainer.addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                webView.requestFocus()
            }
        }
    }

    /**
     * Sends fake gamepad state directly to the WebView on every change without batching.
     */
    fun sendGamepadStateImmediate(state: GamepadState) {
        val js = String.format(
            Locale.US,
            "window.__xcloudPadUpdateGamepad && window.__xcloudPadUpdateGamepad(%d, %.4f, %.4f, %.4f, %.4f, %.4f, %.4f);",
            state.buttonsMask,
            state.lt,
            state.rt,
            state.lx,
            state.ly,
            state.rx,
            state.ry
        )
        val dispatch = Runnable {
            if (!webView.hasFocus()) {
                webView.requestFocus()
            }
            webView.evaluateJavascript(js, null)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch.run()
        } else {
            mainHandler.post(dispatch)
        }
    }

    fun applyStreamEngineSettings(settings: StreamEngineSettings) {
        currentSettings = settings
        val json = repository.serializeStreamSettings(settings).toString()
        val js = "window.__xcloudPadAction && window.__xcloudPadAction('apply_settings', ${JSONObject.quote(json)});"
        mainHandler.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun executeStreamAction(action: String) {
        val js = "window.__xcloudPadAction && window.__xcloudPadAction(${JSONObject.quote(action)}, '{}');"
        mainHandler.post {
            webView.evaluateJavascript(js, null)
        }
    }

    fun reloadPlayHome() {
        mainHandler.post {
            webView.loadUrl(XCLOUD_PLAY_URL)
        }
    }

    fun flushCookies() {
        runCatching { CookieManager.getInstance().flush() }
    }

    fun releaseAndDestroyWebView() {
        mainHandler.post {
            runCatching {
                CookieManager.getInstance().flush()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        }
    }

    inner class XCloudPadJsBridge {
        @JavascriptInterface
        fun gmGetValue(key: String, defaultJson: String): String {
            return repository.gmMemoryCache[key] ?: defaultJson
        }

        @JavascriptInterface
        fun gmSetValue(key: String, rawJsonValue: String) {
            scope.launch {
                repository.setGmValue(key, rawJsonValue)
            }
        }

        @JavascriptInterface
        fun gmDeleteValue(key: String) {
            scope.launch {
                repository.deleteGmValue(key)
            }
        }

        @JavascriptInterface
        fun onStreamStateChanged(isStreaming: Boolean, gameTitle: String) {
            mainHandler.post {
                onStreamStateUpdate(isStreaming, gameTitle)
            }
        }

        @JavascriptInterface
        fun onStreamTelemetry(telemetryJson: String) {
            runCatching {
                val o = JSONObject(telemetryJson)
                val telemetry = StreamTelemetry(
                    isStreaming = o.optBoolean("isStreaming", false),
                    gameTitle = o.optString("gameTitle", ""),
                    fps = o.optInt("fps", 0),
                    width = o.optInt("width", 0),
                    height = o.optInt("height", 0),
                    bitrateKbps = o.optInt("bitrateKbps", 0),
                    rttMs = o.optInt("rttMs", 0),
                    decodeMs = o.optDouble("decodeMs", 0.0).toFloat(),
                    framesDropped = o.optInt("framesDropped", 0),
                    packetLossPct = o.optDouble("packetLossPct", 0.0).toFloat(),
                    detectedGamepads = o.optInt("detectedGamepads", 1)
                )
                mainHandler.post {
                    onTelemetryUpdate(telemetry)
                }
            }
        }

        @JavascriptInterface
        fun logMessage(level: String, message: String) {
            mainHandler.post {
                onEngineLog("[$level] $message")
            }
        }
    }
}
