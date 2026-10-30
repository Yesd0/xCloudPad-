package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ControllerProfile
import com.example.model.ExternalDisplayInfo
import com.example.model.GamepadState
import com.example.model.GyroMode
import com.example.model.SensitivityCurve
import com.example.model.StickMode
import com.example.model.StreamEngineSettings
import com.example.model.StreamTelemetry
import com.example.model.TvAspectRatioMode
import com.example.model.UpscalerTier
import com.example.model.XboxButton
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Semi-transparent monochrome edge handle on the right side of the screen.
 */
@Composable
fun RightEdgeSlideHandle(
    isStreaming: Boolean,
    isCasting: Boolean,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 28.dp, height = 96.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            .background(Color(0x990A0A0A))
            .border(
                width = 1.2.dp,
                color = if (isCasting || isStreaming) Color.White else GlassBorder,
                shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
            )
            .clickable(onClick = onOpenMenu)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (dragAmount < -6f) {
                        change.consume()
                        onOpenMenu()
                    }
                }
            }
            .testTag("right_edge_menu_handle")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "Open xCloud Pad Side Menu",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 24.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.70f))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SideMenuPanel(
    visible: Boolean,
    telemetry: StreamTelemetry,
    streamSettings: StreamEngineSettings,
    profiles: List<ControllerProfile>,
    activeProfile: ControllerProfile,
    availableDisplays: List<ExternalDisplayInfo>,
    activeCastDisplay: ExternalDisplayInfo?,
    showControllerOverlay: Boolean,
    showDebugPanel: Boolean,
    onClose: () -> Unit,
    onToggleControllerOverlay: (Boolean) -> Unit,
    onToggleDebugPanel: (Boolean) -> Unit,
    onOpenLayoutEditor: () -> Unit,
    onStreamAction: (String) -> Unit,
    onReloadHome: () -> Unit,
    onUpdateStreamSettings: (StreamEngineSettings) -> Unit,
    onSelectProfile: (String) -> Unit,
    onUpdateActiveProfile: (ControllerProfile) -> Unit,
    onCreateGameProfile: (String, String) -> Unit,
    onStartCasting: (Int, TvAspectRatioMode) -> Unit,
    onStopCasting: () -> Unit,
    onRefreshDisplays: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var newProfileName by remember { mutableStateOf("") }

    LaunchedEffect(visible, interactionTick, selectedTab) {
        if (visible) {
            delay(8500L)
            onClose()
        }
    }

    fun touchActivity() {
        interactionTick++
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            color = ObsidianSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
            modifier = Modifier
                .width(360.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .border(
                    width = 1.4.dp,
                    color = Color.White.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
                )
                .testTag("side_menu_panel")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Column {
                            Text(
                                text = "xCloud Pad",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (telemetry.isStreaming) {
                                    "Streaming • ${telemetry.fps} FPS • ${telemetry.rttMs}ms"
                                } else {
                                    "Monochrome Crystal Pad Active"
                                },
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("btn_close_side_menu")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Menu", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Prominent Edit Control HUD Banner right at the top of Settings
                Button(
                    onClick = {
                        touchActivity()
                        onOpenLayoutEditor()
                        onClose()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_edit_hud_top_settings")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Edit Gamepad HUD (Move & Resize Buttons)",
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 4 Section Tabs: 1. Quick, 2. Video, 3. Cast, 4. Controller
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SideMenuTabButton(
                        title = "Quick",
                        icon = Icons.Default.SportsEsports,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; touchActivity() },
                        modifier = Modifier.weight(1f).testTag("tab_quick")
                    )
                    SideMenuTabButton(
                        title = "Video",
                        icon = Icons.Default.Tune,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; touchActivity() },
                        modifier = Modifier.weight(1f).testTag("tab_video")
                    )
                    SideMenuTabButton(
                        title = "Cast",
                        icon = if (activeCastDisplay != null) Icons.Default.CastConnected else Icons.Default.Cast,
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2; touchActivity() },
                        modifier = Modifier.weight(1f).testTag("tab_cast")
                    )
                    SideMenuTabButton(
                        title = "Pad",
                        icon = Icons.Default.Settings,
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3; touchActivity() },
                        modifier = Modifier.weight(1f).testTag("tab_pad")
                    )
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.18f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Tab Content
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> {
                            Text(
                                text = "Xbox Quick Actions",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                QuickActionCard(
                                    label = "Xbox Guide",
                                    icon = Icons.Default.SportsEsports,
                                    onClick = { touchActivity(); onStreamAction("xbox_guide") },
                                    modifier = Modifier.weight(1f).testTag("action_xbox_guide")
                                )
                                QuickActionCard(
                                    label = "Menu",
                                    icon = Icons.Default.MoreHoriz,
                                    onClick = { touchActivity(); onStreamAction("open_stream_menu") },
                                    modifier = Modifier.weight(1f).testTag("action_stream_options")
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                QuickActionCard(
                                    label = "Mic",
                                    icon = Icons.Default.Mic,
                                    onClick = { touchActivity(); onStreamAction("toggle_mic") },
                                    modifier = Modifier.weight(1f).testTag("action_toggle_mic")
                                )
                                QuickActionCard(
                                    label = "Captions",
                                    icon = Icons.Default.ClosedCaption,
                                    onClick = { touchActivity(); onStreamAction("toggle_captions") },
                                    modifier = Modifier.weight(1f).testTag("action_captions")
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                QuickActionCard(
                                    label = "Home",
                                    icon = Icons.Default.Refresh,
                                    onClick = { touchActivity(); onReloadHome() },
                                    modifier = Modifier.weight(1f).testTag("action_reload_home")
                                )
                                QuickActionCard(
                                    label = "Quit",
                                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                                    onClick = { touchActivity(); onStreamAction("quit_game") },
                                    modifier = Modifier.weight(1f).testTag("action_quit_game")
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))

                            SettingSwitchRow(
                                title = "Virtual Gamepad HUD",
                                subtitle = "Show B&W crystal controller overlay",
                                checked = showControllerOverlay,
                                onCheckedChange = { touchActivity(); onToggleControllerOverlay(it) }
                            )
                            SettingSwitchRow(
                                title = "Debug Panel",
                                subtitle = "Show live telemetry and engine logs",
                                checked = showDebugPanel,
                                onCheckedChange = { touchActivity(); onToggleDebugPanel(it) }
                            )
                        }

                        1 -> {
                            Text(
                                text = "Engine & Visual Filters",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Resolution
                            Text("Target Resolution", color = TextSecondary, fontSize = 11.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("720p", "1080p", "1080p_hq").forEach { key ->
                                    FilterChip(
                                        selected = streamSettings.targetResolution == key,
                                        onClick = {
                                            touchActivity()
                                            onUpdateStreamSettings(streamSettings.copy(targetResolution = key))
                                        },
                                        label = { Text(key.uppercase().replace("_", " "), fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            // Bitrate
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Max Bitrate", color = TextSecondary, fontSize = 11.sp)
                                    Text("${streamSettings.maxBitrateKbps / 1000} Mbps", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = streamSettings.maxBitrateKbps.toFloat(),
                                    onValueChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(maxBitrateKbps = (it / 1000f).roundToInt() * 1000)) },
                                    valueRange = 3000f..35000f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                            // Visual Filters
                            Text("Visual Enhancements", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            
                            // Sharpness
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Sharpness", color = TextSecondary, fontSize = 11.sp)
                                    Text("${streamSettings.videoSharpness}%", color = Color.White, fontSize = 11.sp)
                                }
                                Slider(
                                    value = streamSettings.videoSharpness.toFloat(),
                                    onValueChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(videoSharpness = it.roundToInt())) },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                )
                            }

                            // Saturation
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Saturation", color = TextSecondary, fontSize = 11.sp)
                                    Text("${streamSettings.videoSaturation}%", color = Color.White, fontSize = 11.sp)
                                }
                                Slider(
                                    value = streamSettings.videoSaturation.toFloat(),
                                    onValueChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(videoSaturation = it.roundToInt())) },
                                    valueRange = 50f..200f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                )
                            }

                            // Contrast
                            Column {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Contrast", color = TextSecondary, fontSize = 11.sp)
                                    Text("${streamSettings.videoContrast}%", color = Color.White, fontSize = 11.sp)
                                }
                                Slider(
                                    value = streamSettings.videoContrast.toFloat(),
                                    onValueChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(videoContrast = it.roundToInt())) },
                                    valueRange = 50f..200f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                )
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                            SettingSwitchRow(
                                title = "Stats Overlay",
                                subtitle = "Show live bitrate/fps on stream",
                                checked = streamSettings.showStatsOverlay,
                                onCheckedChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(showStatsOverlay = it)) }
                            )

                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                            // 4K GPU Upscaler Section
                            Text(
                                text = "4K GPU Upscaler (WebGL2)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            // Upscaler Tier
                            Text("Performance Tier", color = TextSecondary, fontSize = 11.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                UpscalerTier.entries.forEach { tier ->
                                    FilterChip(
                                        selected = streamSettings.upscalerTier == tier,
                                        onClick = {
                                            touchActivity()
                                            onUpdateStreamSettings(streamSettings.copy(upscalerTier = tier))
                                        },
                                        label = { Text(tier.label, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            if (streamSettings.upscalerTier != UpscalerTier.OFF) {
                                // Detail Strength
                                Column {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("3D Detail Strength", color = TextSecondary, fontSize = 11.sp)
                                        Text("${streamSettings.upscalerDetailStrength}%", color = Color.White, fontSize = 11.sp)
                                    }
                                    Slider(
                                        value = streamSettings.upscalerDetailStrength.toFloat(),
                                        onValueChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(upscalerDetailStrength = it.roundToInt())) },
                                        valueRange = 0f..100f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                    )
                                }

                                // Deblock Strength
                                Column {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Deblock & Denoise", color = TextSecondary, fontSize = 11.sp)
                                        Text("${streamSettings.upscalerDeblockStrength}%", color = Color.White, fontSize = 11.sp)
                                    }
                                    Slider(
                                        value = streamSettings.upscalerDeblockStrength.toFloat(),
                                        onValueChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(upscalerDeblockStrength = it.roundToInt())) },
                                        valueRange = 0f..100f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                    )
                                }

                                SettingSwitchRow(
                                    title = "Film Grain",
                                    subtitle = "Reduces compression banding in dark scenes",
                                    checked = streamSettings.upscalerGrainEnabled,
                                    onCheckedChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(upscalerGrainEnabled = it)) }
                                )

                                SettingSwitchRow(
                                    title = "Upscaler Test Pattern",
                                    subtitle = "Show diagonal lines to verify clarity",
                                    checked = streamSettings.upscalerTestPattern,
                                    onCheckedChange = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(upscalerTestPattern = it)) }
                                )
                            }

                            OutlinedButton(
                                onClick = { touchActivity(); onStreamAction("open_engine_settings"); onClose() },
                                modifier = Modifier.fillMaxWidth().testTag("btn_engine_dialog")
                            ) {
                                Text("Advanced Engine Dialog", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        2 -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("TV Cast Settings", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { touchActivity(); onRefreshDisplays() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                }
                            }

                            if (activeCastDisplay != null) {
                                Surface(
                                    color = ObsidianCard,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().border(1.2.dp, Color.White, RoundedCornerShape(14.dp))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Active Cast: ${activeCastDisplay.name}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = { touchActivity(); onStopCasting() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Stop Casting", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                if (availableDisplays.isEmpty()) {
                                    Text("No displays found. Connect via HDMI or Wireless Display.", color = TextSecondary, fontSize = 11.sp)
                                } else {
                                    availableDisplays.forEach { disp ->
                                        Surface(
                                            color = ObsidianCard,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().clickable { onStartCasting(disp.displayId, streamSettings.tvAspectRatioMode) }.padding(vertical = 4.dp)
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(disp.name, color = Color.White, fontSize = 13.sp)
                                                    Text("${disp.width}x${disp.height}", color = TextSecondary, fontSize = 11.sp)
                                                }
                                                Icon(Icons.Default.Cast, contentDescription = null, tint = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text("TV Aspect Ratio", color = TextSecondary, fontSize = 11.sp)
                            TvAspectRatioMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = streamSettings.tvAspectRatioMode == mode,
                                    onClick = { touchActivity(); onUpdateStreamSettings(streamSettings.copy(tvAspectRatioMode = mode)) },
                                    label = { Text(mode.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black),
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                            }
                        }

                        3 -> {
                            Text(
                                text = "Controller Profiles & HUD Customization",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = {
                                    touchActivity()
                                    onOpenLayoutEditor()
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                modifier = Modifier.fillMaxWidth().testTag("btn_open_layout_editor")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Edit HUD Controls (Tap, Move & Resize Any Button)",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            }

                            Text("Saved Per-Game Profiles", color = TextSecondary, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                profiles.forEach { prof ->
                                    FilterChip(
                                        selected = prof.id == activeProfile.id,
                                        onClick = { touchActivity(); onSelectProfile(prof.id) },
                                        label = { Text(prof.name, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = newProfileName,
                                    onValueChange = { touchActivity(); newProfileName = it },
                                    placeholder = { Text("New game profile name...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        touchActivity()
                                        val trimmed = newProfileName.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onCreateGameProfile(trimmed, telemetry.gameTitle.ifBlank { trimmed })
                                            newProfileName = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Save Profile", tint = Color.White)
                                }
                            }

                            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                            Text("Thumbstick Mode", color = TextSecondary, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StickMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = activeProfile.stickMode == mode,
                                        onClick = {
                                            touchActivity()
                                            onUpdateActiveProfile(activeProfile.copy(stickMode = mode))
                                        },
                                        label = { Text(mode.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Stick Deadzone", color = TextSecondary, fontSize = 12.sp)
                                    Text("${(activeProfile.deadzone * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp)
                                }
                                Slider(
                                    value = activeProfile.deadzone,
                                    onValueChange = {
                                        touchActivity()
                                        onUpdateActiveProfile(activeProfile.copy(deadzone = it))
                                    },
                                    valueRange = 0.0f..0.28f,
                                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                )
                            }

                            Text("Stick Response Curve", color = TextSecondary, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SensitivityCurve.entries.forEach { curve ->
                                    FilterChip(
                                        selected = activeProfile.sensitivityCurve == curve,
                                        onClick = {
                                            touchActivity()
                                            onUpdateActiveProfile(activeProfile.copy(sensitivityCurve = curve))
                                        },
                                        label = { Text(curve.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }

                            SettingSwitchRow(
                                title = "Slide Between Buttons (ABXY & D-Pad)",
                                subtitle = "Roll thumb smoothly across A/B/X/Y without lifting",
                                checked = activeProfile.slideBetweenButtons,
                                onCheckedChange = {
                                    touchActivity()
                                    onUpdateActiveProfile(activeProfile.copy(slideBetweenButtons = it))
                                }
                            )
                            SettingSwitchRow(
                                title = "Double-Tap Stick for L3 / R3",
                                subtitle = "Quick double-tap on left or right stick clicks L3/R3",
                                checked = activeProfile.doubleTapStickClick,
                                onCheckedChange = {
                                    touchActivity()
                                    onUpdateActiveProfile(activeProfile.copy(doubleTapStickClick = it))
                                }
                            )

                            Text("Gyro Aiming (Right Stick)", color = TextSecondary, fontSize = 12.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GyroMode.entries.forEach { gMode ->
                                    FilterChip(
                                        selected = activeProfile.gyroMode == gMode,
                                        onClick = {
                                            touchActivity()
                                            onUpdateActiveProfile(activeProfile.copy(gyroMode = gMode))
                                        },
                                        label = { Text(gMode.label, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color.White,
                                            selectedLabelColor = Color.Black
                                        )
                                    )
                                }
                            }
                            if (activeProfile.gyroMode != GyroMode.OFF) {
                                Column {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Gyro Sensitivity", color = TextSecondary, fontSize = 12.sp)
                                        Text(
                                            String.format(Locale.US, "%.1fx", activeProfile.gyroSensitivity),
                                            color = Color.White,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Slider(
                                        value = activeProfile.gyroSensitivity,
                                        onValueChange = {
                                            touchActivity()
                                            onUpdateActiveProfile(activeProfile.copy(gyroSensitivity = it))
                                        },
                                        valueRange = 0.3f..3.0f,
                                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideMenuTabButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) Color.White else ObsidianCard,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                color = if (selected) Color.Black else Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianCard)
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, fontSize = 10.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = Color.White,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
fun DebugTelemetryOverlay(
    gamepadState: GamepadState,
    telemetry: StreamTelemetry,
    activeCastDisplay: ExternalDisplayInfo?,
    phoneDisplaySummary: String,
    aspectRatioMode: TvAspectRatioMode,
    recentLogs: List<String>,
    onStreamAction: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pressedNames = remember(gamepadState.buttonsMask, gamepadState.lt, gamepadState.rt) {
        (0 until XboxButton.COUNT)
            .filter { gamepadState.isPressed(it) }
            .map { XboxButton.label(it) }
    }

    Surface(
        color = ObsidianBg.copy(alpha = 0.90f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .width(310.dp)
            .border(1.2.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("debug_telemetry_panel")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "GAMEPAD & CAST TELEMETRY",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(22.dp).testTag("btn_close_debug")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Hide Debug", tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
            }

            Text(
                text = "Page Detected Gamepads: ${telemetry.detectedGamepads} (Standard 17B/4A)",
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Pressed [${pressedNames.size}/17]: ${if (pressedNames.isEmpty()) "None" else pressedNames.joinToString(", ")}",
                color = TextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format(
                    Locale.US,
                    "Axes: LX=%+.2f LY=%+.2f | RX=%+.2f RY=%+.2f",
                    gamepadState.lx,
                    gamepadState.ly,
                    gamepadState.rx,
                    gamepadState.ry
                ),
                color = TextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = String.format(
                    Locale.US,
                    "Triggers: LT=%.2f RT=%.2f | Mask=0x%05X",
                    gamepadState.lt,
                    gamepadState.rt,
                    gamepadState.buttonsMask
                ),
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            Text(
                text = if (activeCastDisplay != null) {
                    "Cast State: TV ACTIVE • ${activeCastDisplay.summary} (${aspectRatioMode.label})"
                } else {
                    "Cast State: PHONE SCREEN • $phoneDisplaySummary"
                },
                color = if (activeCastDisplay != null) Color.White else TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Stream: ${telemetry.width}x${telemetry.height} @ ${telemetry.fps}fps • ${(telemetry.bitrateKbps / 1000f)}Mbps • RTT ${telemetry.rttMs}ms",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { onStreamAction("force_gamepad_sync") },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(28.dp).fillMaxWidth()
            ) {
                Text("Force Controller Re-Sync", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            if (recentLogs.isNotEmpty()) {
                Text(
                    text = "Engine: ${recentLogs.last().take(65)}",
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
