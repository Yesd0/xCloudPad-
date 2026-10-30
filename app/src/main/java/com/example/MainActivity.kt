package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ExternalDisplayInfo
import com.example.model.StreamTelemetry
import com.example.model.TvAspectRatioMode
import com.example.ui.components.DebugTelemetryOverlay
import com.example.ui.components.GamepadOverlay
import com.example.ui.components.RightEdgeSlideHandle
import com.example.ui.components.SideMenuPanel
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.service.StreamingForegroundService
import com.example.viewmodel.XCloudPadViewModel
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val viewModel: XCloudPadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        // Start the system monitoring service
        com.example.service.StreamingForegroundService.start(this)
        com.example.service.StreamingForegroundService.onEndStreamRequested = {
            finishAffinity()
        }

        setContent {
            MyApplicationTheme {
                XCloudPadMainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.webViewManager.flushCookies()
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.service.StreamingForegroundService.onEndStreamRequested = null
        com.example.service.StreamingForegroundService.stop(this)
    }
}

@Composable
fun XCloudPadMainScreen(viewModel: XCloudPadViewModel) {
    val gamepadState by viewModel.gamepadState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val streamSettings by viewModel.streamSettings.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val availableDisplays by viewModel.availableDisplays.collectAsStateWithLifecycle()
    val activeCastDisplay by viewModel.activeCastDisplay.collectAsStateWithLifecycle()
    val showControllerOverlay by viewModel.showControllerOverlay.collectAsStateWithLifecycle()
    val showDebugPanel by viewModel.showDebugPanel.collectAsStateWithLifecycle()
    val isSideMenuOpen by viewModel.isSideMenuOpen.collectAsStateWithLifecycle()
    val isLayoutEditorMode by viewModel.isLayoutEditorMode.collectAsStateWithLifecycle()
    val selectedControlElement by viewModel.selectedControlElement.collectAsStateWithLifecycle()
    val engineLogs by viewModel.engineLogs.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val phoneDisplaySummary = remember(context) {
        val dm = context.resources.displayMetrics
        val hz = try {
            @Suppress("DEPRECATION")
            val d: Display? = (context as? ComponentActivity)?.windowManager?.defaultDisplay
            (d?.refreshRate ?: 60f).roundToInt()
        } catch (_: Exception) {
            60
        }
        "${dm.widthPixels}x${dm.heightPixels} @ ${hz}Hz"
    }

    BackHandler(enabled = isSideMenuOpen || isLayoutEditorMode) {
        if (isLayoutEditorMode) {
            viewModel.setLayoutEditorMode(false)
        } else if (isSideMenuOpen) {
            viewModel.setSideMenuOpen(false)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("xcloud_pad_root")
    ) {
        val isPortrait = maxHeight >= maxWidth
        val isCasting = activeCastDisplay != null

        // 1. STREAM VIDEO LAYER (Always full screen in Landscape)
        if (!isCasting) {
            PhoneWebViewHost(
                onBindContainer = { viewModel.bindPhoneWebViewContainer(it) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CastControllerPhoneBackdrop(
                activeDisplay = activeCastDisplay!!,
                telemetry = telemetry,
                aspectRatioMode = streamSettings.tvAspectRatioMode,
                onCycleAspectRatio = {
                    val modes = TvAspectRatioMode.entries
                    val nextIdx = (modes.indexOf(streamSettings.tvAspectRatioMode) + 1) % modes.size
                    viewModel.updateStreamEngineSettings(
                        streamSettings.copy(tvAspectRatioMode = modes[nextIdx])
                    )
                },
                onStopCasting = { viewModel.stopCastingToTv() }
            )
        }

        // 2. SIMPLE TOP-RIGHT SETTINGS BUTTON
        if (!isLayoutEditorMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
            ) {
                Surface(
                    color = Color(0xBB080808),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(44.dp)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { viewModel.setSideMenuOpen(true) }
                        .testTag("btn_open_settings_simple")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 3. ON-SCREEN MULTITOUCH CRYSTAL GAMEPAD OVERLAY
        if (showControllerOverlay || isCasting) {
            GamepadOverlay(
                isPortrait = false, // Always landscape
                gamepadState = gamepadState,
                profile = activeProfile,
                isLayoutEditorMode = isLayoutEditorMode,
                selectedElement = selectedControlElement,
                onSelectElement = { viewModel.selectControlElement(it) },
                onUpdateTransform = { id, transform ->
                    viewModel.updateControlTransform(false, id, transform)
                },
                onResetLayout = { viewModel.resetActiveLayout(false) },
                onExitLayoutEditor = { viewModel.setLayoutEditorMode(false) },
                onButtonChange = { btn, down -> viewModel.updateButtonState(btn, down) },
                onMultiButtonChange = { pressed, released ->
                    viewModel.updateMultiButtons(pressed, released)
                },
                onTriggerChange = { isLeft, v -> viewModel.updateTriggerState(isLeft, v) },
                onStickChange = { isLeft, x, y -> viewModel.updateStickState(isLeft, x, y) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 4. DEBUG TELEMETRY PANEL
        if (showDebugPanel) {
            DebugTelemetryOverlay(
                gamepadState = gamepadState,
                telemetry = telemetry,
                activeCastDisplay = activeCastDisplay,
                phoneDisplaySummary = phoneDisplaySummary,
                aspectRatioMode = streamSettings.tvAspectRatioMode,
                recentLogs = engineLogs,
                onStreamAction = { viewModel.webViewManager.executeStreamAction(it) },
                onClose = { viewModel.setDebugPanelVisible(false) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 10.dp, top = 46.dp)
            )
        }

        // 5. RIGHT-EDGE SEMI-TRANSPARENT SLIDE HANDLE
        if (!isSideMenuOpen && !isLayoutEditorMode) {
            RightEdgeSlideHandle(
                isStreaming = telemetry.isStreaming,
                isCasting = isCasting,
                onOpenMenu = { viewModel.setSideMenuOpen(true) },
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        // 6. AUTO-HIDING SLIDE-IN SIDE MENU
        SideMenuPanel(
            visible = isSideMenuOpen,
            telemetry = telemetry,
            streamSettings = streamSettings,
            profiles = profiles,
            activeProfile = activeProfile,
            availableDisplays = availableDisplays,
            activeCastDisplay = activeCastDisplay,
            showControllerOverlay = showControllerOverlay,
            showDebugPanel = showDebugPanel,
            onClose = { viewModel.setSideMenuOpen(false) },
            onToggleControllerOverlay = { viewModel.setControllerOverlayVisible(it) },
            onToggleDebugPanel = { viewModel.setDebugPanelVisible(it) },
            onOpenLayoutEditor = { viewModel.setLayoutEditorMode(true) },
            onStreamAction = { viewModel.webViewManager.executeStreamAction(it) },
            onReloadHome = { viewModel.webViewManager.reloadPlayHome() },
            onUpdateStreamSettings = { viewModel.updateStreamEngineSettings(it) },
            onSelectProfile = { viewModel.selectProfile(it) },
            onUpdateActiveProfile = { viewModel.updateActiveProfile(it) },
            onCreateGameProfile = { name, game -> viewModel.createGameProfile(name, game) },
            onStartCasting = { dispId, aspect -> viewModel.startCastingToTv(dispId, aspect) },
            onStopCasting = { viewModel.stopCastingToTv() },
            onRefreshDisplays = { viewModel.refreshExternalDisplays() },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun PhoneWebViewHost(
    onBindContainer: (FrameLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            FrameLayout(ctx).apply {
                setBackgroundColor(AndroidColor.BLACK)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                onBindContainer(this)
            }
        },
        update = { container ->
            onBindContainer(container)
        },
        modifier = modifier.testTag("phone_webview_host")
    )
}


@Composable
private fun CastControllerPhoneBackdrop(
    activeDisplay: ExternalDisplayInfo,
    telemetry: StreamTelemetry,
    aspectRatioMode: TvAspectRatioMode,
    onCycleAspectRatio: () -> Unit,
    onStopCasting: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF171717), ObsidianBg)
                )
            )
            .testTag("tv_cast_phone_backdrop")
    ) {
        Surface(
            color = ObsidianCard.copy(alpha = 0.94f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 42.dp)
                .border(1.2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "TV: ${activeDisplay.summary}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${telemetry.fps} FPS • Ping ${telemetry.rttMs}ms • Dec ${telemetry.decodeMs}ms",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onCycleAspectRatio)
                        .testTag("btn_cycle_tv_aspect")
                ) {
                    Text(
                        text = aspectRatioMode.label.substringBefore(" "),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Button(
                    onClick = onStopCasting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("btn_stop_casting_banner")
                ) {
                    Text(
                        text = "Stop Casting",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
