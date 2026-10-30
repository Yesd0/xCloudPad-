package com.example.cast

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.model.ExternalDisplayInfo
import com.example.model.TvAspectRatioMode
import com.example.webview.XCloudWebViewManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Presentation window shown on an external TV display (Miracast, Wireless Display, or USB-C to HDMI).
 * Hosts the single live WebView inside an aspect-ratio-enforcing black frame so nothing is cropped or stretched.
 */
class TvCastPresentation(
    outerContext: Context,
    display: Display,
    private val webViewManager: XCloudWebViewManager,
    private var aspectRatioMode: TvAspectRatioMode,
    private var streamWidth: Int = 1920,
    private var streamHeight: Int = 1080
) : Presentation(outerContext, display) {

    private lateinit var rootBlackFrame: FrameLayout
    private lateinit var aspectContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootBlackFrame = object : FrameLayout(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (w > 0 && h > 0) {
                    post { updateAspectLayout(w, h) }
                }
            }
        }.apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        aspectContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        }

        rootBlackFrame.addView(aspectContainer)
        setContentView(rootBlackFrame)

        // Move the single live WebView into the TV presentation's aspect container
        webViewManager.attachWebViewTo(aspectContainer, context)
        rootBlackFrame.post {
            updateAspectLayout(rootBlackFrame.width, rootBlackFrame.height)
        }
    }

    fun updateAspectAndResolution(
        newMode: TvAspectRatioMode,
        newStreamWidth: Int,
        newStreamHeight: Int
    ) {
        aspectRatioMode = newMode
        if (newStreamWidth > 0 && newStreamHeight > 0) {
            streamWidth = newStreamWidth
            streamHeight = newStreamHeight
        }
        if (::rootBlackFrame.isInitialized && rootBlackFrame.width > 0 && rootBlackFrame.height > 0) {
            updateAspectLayout(rootBlackFrame.width, rootBlackFrame.height)
        }
    }

    private fun updateAspectLayout(parentW: Int, parentH: Int) {
        if (!::aspectContainer.isInitialized || parentW <= 0 || parentH <= 0) return

        val srcW = if (streamWidth > 0) streamWidth else 1920
        val srcH = if (streamHeight > 0) streamHeight else 1080
        val srcAspect = srcW.toFloat() / srcH.toFloat()

        // Detect real physical resolution for upscaler
        val mode = display.mode
        val physicalW = mode?.physicalWidth ?: parentW
        val physicalH = mode?.physicalHeight ?: parentH
        
        // Push resolution to upscaler settings if changed
        webViewManager.scope.launch {
            try {
                val current = webViewManager.repository.streamSettingsFlow.first()
                if (current.upscalerOutputWidth != physicalW || current.upscalerOutputHeight != physicalH) {
                    webViewManager.repository.saveStreamSettings(current.copy(
                        upscalerOutputWidth = physicalW,
                        upscalerOutputHeight = physicalH
                    ))
                }
            } catch (_: Exception) {}
        }

        val (targetW, targetH) = when (aspectRatioMode) {
            TvAspectRatioMode.FIT -> {
                // Fit with black letterbox or pillarbox bars so nothing is cropped or stretched
                val screenAspect = parentW.toFloat() / parentH.toFloat()
                if (screenAspect > srcAspect) {
                    val w = (parentH * srcAspect).roundToInt().coerceAtMost(parentW)
                    w to parentH
                } else {
                    val h = (parentW / srcAspect).roundToInt().coerceAtMost(parentH)
                    parentW to h
                }
            }
            TvAspectRatioMode.FILL_16_9 -> {
                val aspect169 = 16f / 9f
                val screenAspect = parentW.toFloat() / parentH.toFloat()
                if (absDiff(screenAspect, aspect169) < 0.08f) {
                    parentW to parentH
                } else if (screenAspect > aspect169) {
                    ((parentH * aspect169).roundToInt()) to parentH
                } else {
                    parentW to ((parentW / aspect169).roundToInt())
                }
            }
            TvAspectRatioMode.RATIO_4_3 -> {
                val aspect43 = 4f / 3f
                val screenAspect = parentW.toFloat() / parentH.toFloat()
                if (screenAspect > aspect43) {
                    ((parentH * aspect43).roundToInt()) to parentH
                } else {
                    parentW to ((parentW / aspect43).roundToInt())
                }
            }
            TvAspectRatioMode.INTEGER_SCALE -> {
                val scaleX = floor(parentW.toFloat() / srcW.toFloat()).toInt()
                val scaleY = floor(parentH.toFloat() / srcH.toFloat()).toInt()
                val intScale = min(scaleX, scaleY)
                if (intScale >= 1) {
                    (srcW * intScale) to (srcH * intScale)
                } else {
                    // Fallback to exact 16:9 fit if display resolution is smaller than 1x stream
                    val screenAspect = parentW.toFloat() / parentH.toFloat()
                    if (screenAspect > srcAspect) {
                        ((parentH * srcAspect).roundToInt()) to parentH
                    } else {
                        parentW to ((parentW / srcAspect).roundToInt())
                    }
                }
            }
        }

        val lp = FrameLayout.LayoutParams(targetW, targetH, Gravity.CENTER)
        aspectContainer.layoutParams = lp
        aspectContainer.requestLayout()
    }

    private fun absDiff(a: Float, b: Float): Float = if (a > b) a - b else b - a
}

class ExternalDisplayController(
    private val context: Context,
    private val webViewManager: XCloudWebViewManager,
    private val onDisplaysChanged: (List<ExternalDisplayInfo>, ExternalDisplayInfo?) -> Unit
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var activePresentation: TvCastPresentation? = null
    private var activeDisplayInfo: ExternalDisplayInfo? = null
    private var phoneWebViewContainer: FrameLayout? = null
    private var currentAspectMode: TvAspectRatioMode = TvAspectRatioMode.FIT
    private var currentStreamWidth: Int = 1920
    private var currentStreamHeight: Int = 1080

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            refreshDisplays()
        }

        override fun onDisplayChanged(displayId: Int) {
            refreshDisplays()
            val currentCast = activeDisplayInfo
            if (currentCast != null && currentCast.displayId == displayId) {
                activePresentation?.updateAspectAndResolution(
                    currentAspectMode,
                    currentStreamWidth,
                    currentStreamHeight
                )
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            val currentCast = activeDisplayInfo
            if (currentCast != null && currentCast.displayId == displayId) {
                // External TV disconnected -> immediately return video to phone screen without ending stream
                stopCasting()
            }
            refreshDisplays()
        }
    }

    fun register() {
        displayManager.registerDisplayListener(displayListener, null)
        refreshDisplays()
    }

    fun unregister() {
        runCatching { displayManager.unregisterDisplayListener(displayListener) }
    }

    fun bindPhoneContainer(container: FrameLayout) {
        phoneWebViewContainer = container
        if (activePresentation == null) {
            webViewManager.attachWebViewTo(container, context)
        }
    }

    fun getAvailableExternalDisplays(): List<ExternalDisplayInfo> {
        val presentationDisplays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val allDisplays = displayManager.displays
        val combined = (presentationDisplays.toList() + allDisplays.filter { it.displayId != Display.DEFAULT_DISPLAY })
            .distinctBy { it.displayId }

        return combined.map { d ->
            val mode = d.mode
            ExternalDisplayInfo(
                displayId = d.displayId,
                name = d.name ?: "External TV (${d.displayId})",
                width = mode?.physicalWidth ?: 1920,
                height = mode?.physicalHeight ?: 1080,
                refreshRateHz = (mode?.refreshRate ?: 60f).roundToInt()
            )
        }
    }

    fun refreshDisplays() {
        val list = getAvailableExternalDisplays()
        if (activeDisplayInfo != null && list.none { it.displayId == activeDisplayInfo?.displayId }) {
            stopCasting()
        }
        onDisplaysChanged(list, activeDisplayInfo)
    }

    fun startCasting(
        targetDisplayId: Int,
        aspectMode: TvAspectRatioMode,
        streamWidth: Int,
        streamHeight: Int
    ): Boolean {
        val display = displayManager.getDisplay(targetDisplayId) ?: return false
        currentAspectMode = aspectMode
        if (streamWidth > 0 && streamHeight > 0) {
            currentStreamWidth = streamWidth
            currentStreamHeight = streamHeight
        }

        runCatching { activePresentation?.dismiss() }
        return try {
            val presentation = TvCastPresentation(
                outerContext = context,
                display = display,
                webViewManager = webViewManager,
                aspectRatioMode = currentAspectMode,
                streamWidth = currentStreamWidth,
                streamHeight = currentStreamHeight
            )
            presentation.show()
            activePresentation = presentation
            val mode = display.mode
            activeDisplayInfo = ExternalDisplayInfo(
                displayId = display.displayId,
                name = display.name ?: "External Display",
                width = mode?.physicalWidth ?: 1920,
                height = mode?.physicalHeight ?: 1080,
                refreshRateHz = (mode?.refreshRate ?: 60f).roundToInt()
            )
            refreshDisplays()
            true
        } catch (_: Exception) {
            stopCasting()
            false
        }
    }

    fun updateCastAspectOrResolution(
        aspectMode: TvAspectRatioMode,
        streamWidth: Int,
        streamHeight: Int
    ) {
        currentAspectMode = aspectMode
        if (streamWidth > 0 && streamHeight > 0) {
            currentStreamWidth = streamWidth
            currentStreamHeight = streamHeight
        }
        activePresentation?.updateAspectAndResolution(
            currentAspectMode,
            currentStreamWidth,
            currentStreamHeight
        )
    }

    fun stopCasting() {
        runCatching { activePresentation?.dismiss() }
        activePresentation = null
        activeDisplayInfo = null
        phoneWebViewContainer?.let { container ->
            webViewManager.attachWebViewTo(container, context)
        }
        onDisplaysChanged(getAvailableExternalDisplays(), null)
    }
}
