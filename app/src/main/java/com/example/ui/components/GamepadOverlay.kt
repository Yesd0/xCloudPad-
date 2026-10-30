package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ControlElementId
import com.example.model.ControlTransform
import com.example.model.ControllerProfile
import com.example.model.GamepadState
import com.example.model.StickMode
import com.example.model.XboxButton
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// Ultra-Clear Monochrome Crystal Palette for Gamepad Controls
private val CrystalIdleFill = Color(0x28000000)
private val CrystalRingIdle = Color(0x78FFFFFF)
private val CrystalRingSubtle = Color(0x40FFFFFF)
private val CrystalPressedFill = Color(0xE6FFFFFF)
private val CrystalPressedGlow = Color(0x55FFFFFF)

@Composable
fun GamepadOverlay(
    isPortrait: Boolean,
    gamepadState: GamepadState,
    profile: ControllerProfile,
    isLayoutEditorMode: Boolean,
    selectedElement: ControlElementId,
    onSelectElement: (ControlElementId) -> Unit,
    onUpdateTransform: (ControlElementId, ControlTransform) -> Unit,
    onResetLayout: () -> Unit,
    onExitLayoutEditor: () -> Unit,
    onButtonChange: (Int, Boolean) -> Unit,
    onMultiButtonChange: (Set<Int>, Set<Int>) -> Unit,
    onTriggerChange: (isLeft: Boolean, value: Float) -> Unit,
    onStickChange: (isLeft: Boolean, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeLayout = if (isPortrait) profile.portraitLayout else profile.landscapeLayout
    val view = LocalView.current

    fun hapticTick() {
        if (profile.hapticsEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("gamepad_overlay_root")
    ) {
        // Subtle alignment grid shown only while in HUD Edit Mode
        if (isLayoutEditorMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 36.dp.toPx()
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
                // Center crosshair reference lines
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1.5f
                )
            }
        }

        // 1. TOP LEFT: Individual LT (Analog Trigger) & LB (Shoulder Button)
        EditableControlContainer(
            elementId = ControlElementId.BTN_LT,
            transform = activeLayout[ControlElementId.BTN_LT]
                ?: activeLayout[ControlElementId.LEFT_TRIGGERS]
                ?: ControlTransform(),
            alignment = Alignment.TopStart,
            baseOffsetX = 6.dp,
            baseOffsetY = if (isPortrait) 48.dp else 8.dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_LT,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            AnalogTriggerControl(
                label = "LT",
                value = gamepadState.lt,
                isEditorMode = isLayoutEditorMode,
                onValueChange = { v ->
                    if ((v > 0.12f) != (gamepadState.lt > 0.12f)) hapticTick()
                    onTriggerChange(true, v)
                },
                modifier = Modifier.testTag("btn_lt_analog")
            )
        }

        EditableControlContainer(
            elementId = ControlElementId.BTN_LB,
            transform = activeLayout[ControlElementId.BTN_LB] ?: ControlTransform(),
            alignment = Alignment.TopStart,
            baseOffsetX = 88.dp,
            baseOffsetY = if (isPortrait) 54.dp else 14.dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_LB,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            CrystalActionButton(
                label = "LB",
                isPressed = gamepadState.isPressed(XboxButton.LB),
                width = 68.dp,
                height = 48.dp,
                cornerRadius = 15.dp,
                isEditorMode = isLayoutEditorMode,
                onPressChanged = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.LB, down)
                },
                modifier = Modifier.testTag("btn_lb")
            )
        }

        // 2. TOP RIGHT: Individual RB (Shoulder Button) & RT (Analog Trigger)
        EditableControlContainer(
            elementId = ControlElementId.BTN_RB,
            transform = activeLayout[ControlElementId.BTN_RB] ?: ControlTransform(),
            alignment = Alignment.TopEnd,
            baseOffsetX = (-88).dp,
            baseOffsetY = if (isPortrait) 54.dp else 14.dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_RB,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            CrystalActionButton(
                label = "RB",
                isPressed = gamepadState.isPressed(XboxButton.RB),
                width = 68.dp,
                height = 48.dp,
                cornerRadius = 15.dp,
                isEditorMode = isLayoutEditorMode,
                onPressChanged = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.RB, down)
                },
                modifier = Modifier.testTag("btn_rb")
            )
        }

        EditableControlContainer(
            elementId = ControlElementId.BTN_RT,
            transform = activeLayout[ControlElementId.BTN_RT]
                ?: activeLayout[ControlElementId.RIGHT_TRIGGERS]
                ?: ControlTransform(),
            alignment = Alignment.TopEnd,
            baseOffsetX = (-6).dp,
            baseOffsetY = if (isPortrait) 48.dp else 8.dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_RT,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            AnalogTriggerControl(
                label = "RT",
                value = gamepadState.rt,
                isEditorMode = isLayoutEditorMode,
                onValueChange = { v ->
                    if ((v > 0.12f) != (gamepadState.rt > 0.12f)) hapticTick()
                    onTriggerChange(false, v)
                },
                modifier = Modifier.testTag("btn_rt_analog")
            )
        }

        // 3. MIDDLE LEFT: Left Thumbstick (LS)
        EditableControlContainer(
            elementId = ControlElementId.LEFT_STICK,
            transform = activeLayout[ControlElementId.LEFT_STICK] ?: ControlTransform(),
            alignment = if (isPortrait) Alignment.CenterStart else Alignment.TopStart,
            baseOffsetX = 10.dp,
            baseOffsetY = if (isPortrait) (-16).dp else 76.dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.LEFT_STICK,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            VirtualThumbstick(
                label = "LS",
                stickMode = profile.stickMode,
                doubleTapStickClick = profile.doubleTapStickClick,
                isStickClicked = gamepadState.isPressed(XboxButton.L3),
                sizeDp = if (isPortrait) 136.dp else 124.dp,
                isEditorMode = isLayoutEditorMode,
                onStickVector = { rawX, rawY ->
                    val (px, py) = profile.processStickVector(rawX, rawY)
                    onStickChange(true, px, py)
                },
                onDoubleTapClick = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.L3, down)
                },
                modifier = Modifier.testTag("stick_left")
            )
        }

        // 4. MIDDLE RIGHT: ABXY Action Diamond + Individual A, B, X, Y Placement Support
        EditableControlContainer(
            elementId = ControlElementId.ABXY_CLUSTER,
            transform = activeLayout[ControlElementId.ABXY_CLUSTER] ?: ControlTransform(),
            alignment = if (isPortrait) Alignment.CenterEnd else Alignment.TopEnd,
            baseOffsetX = (-10).dp,
            baseOffsetY = if (isPortrait) (-16).dp else 72.dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.ABXY_CLUSTER ||
                selectedElement == ControlElementId.BTN_A ||
                selectedElement == ControlElementId.BTN_B ||
                selectedElement == ControlElementId.BTN_X ||
                selectedElement == ControlElementId.BTN_Y,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            AbxyDiamondCluster(
                gamepadState = gamepadState,
                slideBetweenButtons = profile.slideBetweenButtons,
                clusterSize = if (isPortrait) 158.dp else 144.dp,
                isEditorMode = isLayoutEditorMode,
                selectedElement = selectedElement,
                activeLayout = activeLayout,
                onSelectElement = onSelectElement,
                onUpdateTransform = onUpdateTransform,
                onButtonsChanged = { pressedSet, releasedSet ->
                    if (pressedSet.isNotEmpty()) hapticTick()
                    onMultiButtonChange(pressedSet, releasedSet)
                },
                modifier = Modifier.testTag("cluster_abxy")
            )
        }

        // 5. LOWER LEFT: 8-Way D-Pad
        EditableControlContainer(
            elementId = ControlElementId.DPAD_CLUSTER,
            transform = activeLayout[ControlElementId.DPAD_CLUSTER] ?: ControlTransform(),
            alignment = Alignment.BottomStart,
            baseOffsetX = if (isPortrait) 16.dp else 118.dp,
            baseOffsetY = if (isPortrait) (-68).dp else (-46).dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.DPAD_CLUSTER,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            EightWayDpadCluster(
                gamepadState = gamepadState,
                slideBetweenButtons = profile.slideBetweenButtons,
                sizeDp = if (isPortrait) 128.dp else 114.dp,
                isEditorMode = isLayoutEditorMode,
                onDpadChanged = { pressedSet, releasedSet ->
                    if (pressedSet.isNotEmpty()) hapticTick()
                    onMultiButtonChange(pressedSet, releasedSet)
                },
                modifier = Modifier.testTag("cluster_dpad")
            )
        }

        // 6. LOWER RIGHT: Right Thumbstick (RS)
        EditableControlContainer(
            elementId = ControlElementId.RIGHT_STICK,
            transform = activeLayout[ControlElementId.RIGHT_STICK] ?: ControlTransform(),
            alignment = Alignment.BottomEnd,
            baseOffsetX = if (isPortrait) (-16).dp else (-118).dp,
            baseOffsetY = if (isPortrait) (-68).dp else (-46).dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.RIGHT_STICK,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            VirtualThumbstick(
                label = "RS",
                stickMode = profile.stickMode,
                doubleTapStickClick = profile.doubleTapStickClick,
                isStickClicked = gamepadState.isPressed(XboxButton.R3),
                sizeDp = if (isPortrait) 130.dp else 118.dp,
                isEditorMode = isLayoutEditorMode,
                onStickVector = { rawX, rawY ->
                    val (px, py) = profile.processStickVector(rawX, rawY)
                    onStickChange(false, px, py)
                },
                onDoubleTapClick = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.R3, down)
                },
                modifier = Modifier.testTag("stick_right")
            )
        }

        // 7. BOTTOM SYSTEM BUTTONS (Individually Clickable, Movable & Resizable in Edit Mode)
        EditableControlContainer(
            elementId = ControlElementId.BTN_L3,
            transform = activeLayout[ControlElementId.BTN_L3] ?: ControlTransform(),
            alignment = Alignment.BottomCenter,
            baseOffsetX = (-124).dp,
            baseOffsetY = (-6).dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_L3,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            CrystalActionButton(
                label = "L3",
                isPressed = gamepadState.isPressed(XboxButton.L3),
                width = 48.dp,
                height = 48.dp,
                cornerRadius = 24.dp,
                isEditorMode = isLayoutEditorMode,
                onPressChanged = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.L3, down)
                },
                modifier = Modifier.testTag("btn_l3")
            )
        }

        EditableControlContainer(
            elementId = ControlElementId.BTN_VIEW,
            transform = activeLayout[ControlElementId.BTN_VIEW] ?: ControlTransform(),
            alignment = Alignment.BottomCenter,
            baseOffsetX = (-64).dp,
            baseOffsetY = (-6).dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_VIEW,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            CrystalActionButton(
                label = "VIEW",
                isPressed = gamepadState.isPressed(XboxButton.VIEW),
                width = 56.dp,
                height = 48.dp,
                cornerRadius = 24.dp,
                isEditorMode = isLayoutEditorMode,
                onPressChanged = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.VIEW, down)
                },
                modifier = Modifier.testTag("btn_view")
            )
        }

        EditableControlContainer(
            elementId = ControlElementId.BTN_XBOX,
            transform = activeLayout[ControlElementId.BTN_XBOX] ?: ControlTransform(),
            alignment = Alignment.BottomCenter,
            baseOffsetX = 0.dp,
            baseOffsetY = (-6).dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_XBOX,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            CrystalNexusButton(
                isPressed = gamepadState.isPressed(XboxButton.XBOX),
                isEditorMode = isLayoutEditorMode,
                onPressChanged = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.XBOX, down)
                },
                modifier = Modifier.testTag("btn_xbox")
            )
        }

        EditableControlContainer(
            elementId = ControlElementId.BTN_MENU,
            transform = activeLayout[ControlElementId.BTN_MENU] ?: ControlTransform(),
            alignment = Alignment.BottomCenter,
            baseOffsetX = 64.dp,
            baseOffsetY = (-6).dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_MENU,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            CrystalActionButton(
                label = "MENU",
                isPressed = gamepadState.isPressed(XboxButton.MENU),
                width = 56.dp,
                height = 48.dp,
                cornerRadius = 24.dp,
                isEditorMode = isLayoutEditorMode,
                onPressChanged = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.MENU, down)
                },
                modifier = Modifier.testTag("btn_menu")
            )
        }

        EditableControlContainer(
            elementId = ControlElementId.BTN_R3,
            transform = activeLayout[ControlElementId.BTN_R3] ?: ControlTransform(),
            alignment = Alignment.BottomCenter,
            baseOffsetX = 124.dp,
            baseOffsetY = (-6).dp,
            isEditorMode = isLayoutEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_R3,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            CrystalActionButton(
                label = "R3",
                isPressed = gamepadState.isPressed(XboxButton.R3),
                width = 48.dp,
                height = 48.dp,
                cornerRadius = 24.dp,
                isEditorMode = isLayoutEditorMode,
                onPressChanged = { down ->
                    if (down) hapticTick()
                    onButtonChange(XboxButton.R3, down)
                },
                modifier = Modifier.testTag("btn_r3")
            )
        }

        // Floating HUD Editor Control Bar
        if (isLayoutEditorMode) {
            val currentTransform = activeLayout[selectedElement] ?: ControlTransform()
            LayoutEditorToolbar(
                selectedElement = selectedElement,
                transform = currentTransform,
                onTransformChanged = { updated -> onUpdateTransform(selectedElement, updated) },
                onApplyToAll = { scale, opacity ->
                    ControlElementId.entries.forEach { id ->
                        val existing = activeLayout[id] ?: ControlTransform()
                        onUpdateTransform(id, existing.copy(scale = scale, opacity = opacity))
                    }
                },
                onResetAll = onResetLayout,
                onDone = onExitLayoutEditor,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Interactive wrapper enabling any button or stick to be tapped, dragged anywhere on screen,
 * and pinch-resized when HUD Edit Mode is enabled.
 */
@Composable
private fun BoxScope.EditableControlContainer(
    elementId: ControlElementId,
    transform: ControlTransform,
    alignment: Alignment,
    baseOffsetX: Dp,
    baseOffsetY: Dp,
    isEditorMode: Boolean,
    isSelected: Boolean,
    onSelect: (ControlElementId) -> Unit,
    onUpdateTransform: (ControlElementId, ControlTransform) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    var dragX by remember(transform.offsetXDp) { mutableFloatStateOf(transform.offsetXDp) }
    var dragY by remember(transform.offsetYDp) { mutableFloatStateOf(transform.offsetYDp) }
    var curScale by remember(transform.scale) { mutableFloatStateOf(transform.scale) }

    val animScale by animateFloatAsState(
        targetValue = curScale.coerceIn(0.50f, 1.80f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "hud_scale_${elementId.name}"
    )

    val editModifier = if (isEditorMode) {
        Modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.38f),
                shape = RoundedCornerShape(18.dp)
            )
            .background(
                color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .pointerInput(elementId) {
                detectTapGestures(onTap = { onSelect(elementId) })
            }
            .pointerInput(elementId, transform.opacity) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onSelect(elementId)
                    dragX += with(density) { pan.x.toDp().value }
                    dragY += with(density) { pan.y.toDp().value }
                    if (zoom != 1f) {
                        curScale = (curScale * zoom).coerceIn(0.50f, 1.80f)
                    }
                    onUpdateTransform(
                        elementId,
                        transform.copy(
                            offsetXDp = dragX,
                            offsetYDp = dragY,
                            scale = curScale
                        )
                    )
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .align(alignment)
            .offset {
                IntOffset(
                    x = with(density) { (baseOffsetX + dragX.dp).roundToPx() },
                    y = with(density) { (baseOffsetY + dragY.dp).roundToPx() }
                )
            }
            .scale(animScale)
            .alpha(transform.opacity.coerceIn(0.15f, 1.0f))
            .then(editModifier)
    ) {
        content()
    }
}

/**
 * Monochrome Crystal Analog Trigger (LT / RT) with smooth vertical fill gauge and spring animation.
 */
@Composable
private fun AnalogTriggerControl(
    label: String,
    value: Float,
    isEditorMode: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = value > 0.04f
    val animValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "trigger_$label"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isActive) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "trigger_scale_$label"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 74.dp, height = 58.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(16.dp))
            .background(CrystalIdleFill)
            .border(
                width = if (isActive) 2.dp else 1.2.dp,
                color = if (isActive) Color.White else CrystalRingIdle,
                shape = RoundedCornerShape(16.dp)
            )
            .then(
                if (!isEditorMode) {
                    Modifier.pointerInput(label) {
                        awaitEachGesture {
                            var startY = 0f
                            val ev = awaitPointerEvent()
                            val down = ev.changes.firstOrNull { it.pressed }
                            if (down != null) {
                                startY = down.position.y
                                val posRatio = (down.position.y / size.height.coerceAtLeast(1)).coerceIn(0.25f, 1f)
                                val pressure = down.pressure.coerceIn(0f, 1f)
                                onValueChange(maxOf(posRatio, if (pressure > 0.1f && pressure < 0.98f) pressure else posRatio))
                            }
                            if (down != null) {
                                do {
                                    val event = awaitPointerEvent()
                                    val active = event.changes.firstOrNull { it.id == down.id && it.pressed }
                                        ?: event.changes.firstOrNull { it.pressed }
                                    if (active != null) {
                                        active.consume()
                                        val dragDelta = ((active.position.y - startY) / (size.height * 0.75f)).coerceIn(-0.5f, 1f)
                                        val posVal = (0.45f + dragDelta).coerceIn(0.15f, 1.0f)
                                        onValueChange(posVal)
                                    }
                                } while (event.changes.any { it.pressed })
                                onValueChange(0f)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillH = size.height * animValue
            if (fillH > 0f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.85f),
                            Color.White.copy(alpha = 0.35f)
                        ),
                        startY = size.height - fillH,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, size.height - fillH),
                    size = Size(size.width, fillH),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (animValue > 0.55f) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = if (isActive) String.format(Locale.US, "%d%%", (value * 100).roundToInt()) else "TRIG",
                color = if (animValue > 0.55f) Color.Black.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.72f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Ultra-Clear Black & White Crystal Button with smooth spring press animation and white illumination.
 */
@Composable
private fun CrystalActionButton(
    label: String,
    isPressed: Boolean,
    width: Dp,
    height: Dp,
    cornerRadius: Dp,
    isEditorMode: Boolean,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "btn_scale_$label"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "btn_glow_$label"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = width, height = height)
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (isPressed) Color.White.copy(alpha = glowAlpha) else CrystalIdleFill
            )
            .border(
                width = if (isPressed) 2.dp else 1.2.dp,
                color = if (isPressed) Color.White else CrystalRingIdle,
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(
                if (!isEditorMode) {
                    Modifier.pointerInput(label) {
                        awaitEachGesture {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed }) {
                                onPressChanged(true)
                                do {
                                    val next = awaitPointerEvent()
                                    next.changes.forEach { it.consume() }
                                } while (next.changes.any { it.pressed })
                                onPressChanged(false)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun CrystalNexusButton(
    isPressed: Boolean,
    isEditorMode: Boolean,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "nexus_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (isPressed) Color.White else CrystalIdleFill)
            .border(
                width = if (isPressed) 2.2.dp else 1.5.dp,
                color = Color.White,
                shape = CircleShape
            )
            .then(
                if (!isEditorMode) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val ev = awaitPointerEvent()
                            if (ev.changes.any { it.pressed }) {
                                onPressChanged(true)
                                do {
                                    val next = awaitPointerEvent()
                                    next.changes.forEach { it.consume() }
                                } while (next.changes.any { it.pressed })
                                onPressChanged(false)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        Icon(
            imageVector = Icons.Default.SportsEsports,
            contentDescription = "Xbox Guide",
            tint = if (isPressed) Color.Black else Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * ABXY Diamond Cluster in ultra-clear Black & White monochrome crystal style.
 * Supports both Slide-Between-Buttons in play mode AND individual A, B, X, Y drag/resize in HUD Edit Mode.
 */
@Composable
private fun AbxyDiamondCluster(
    gamepadState: GamepadState,
    slideBetweenButtons: Boolean,
    clusterSize: Dp,
    isEditorMode: Boolean,
    selectedElement: ControlElementId,
    activeLayout: Map<ControlElementId, ControlTransform>,
    onSelectElement: (ControlElementId) -> Unit,
    onUpdateTransform: (ControlElementId, ControlTransform) -> Unit,
    onButtonsChanged: (pressed: Set<Int>, released: Set<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val allAbxy = remember { setOf(XboxButton.A, XboxButton.B, XboxButton.X, XboxButton.Y) }
    val density = LocalDensity.current

    val tY = activeLayout[ControlElementId.BTN_Y] ?: ControlTransform()
    val tX = activeLayout[ControlElementId.BTN_X] ?: ControlTransform()
    val tB = activeLayout[ControlElementId.BTN_B] ?: ControlTransform()
    val tA = activeLayout[ControlElementId.BTN_A] ?: ControlTransform()

    Box(
        modifier = modifier
            .size(clusterSize)
            .then(
                if (!isEditorMode) {
                    Modifier.pointerInput(slideBetweenButtons, tY, tX, tB, tA) {
                        awaitEachGesture {
                            var currentHeld = emptySet<Int>()

                            fun resolveButtonsAt(points: List<Offset>): Set<Int> {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val radius = size.width * 0.33f
                                val centers = mapOf(
                                    XboxButton.Y to Offset(
                                        cx + with(density) { tY.offsetXDp.dp.toPx() },
                                        (cy - radius) + with(density) { tY.offsetYDp.dp.toPx() }
                                    ),
                                    XboxButton.B to Offset(
                                        (cx + radius) + with(density) { tB.offsetXDp.dp.toPx() },
                                        cy + with(density) { tB.offsetYDp.dp.toPx() }
                                    ),
                                    XboxButton.A to Offset(
                                        cx + with(density) { tA.offsetXDp.dp.toPx() },
                                        (cy + radius) + with(density) { tA.offsetYDp.dp.toPx() }
                                    ),
                                    XboxButton.X to Offset(
                                        (cx - radius) + with(density) { tX.offsetXDp.dp.toPx() },
                                        cy + with(density) { tX.offsetYDp.dp.toPx() }
                                    )
                                )
                                val hitRadius = size.width * 0.27f
                                val hitSet = mutableSetOf<Int>()
                                for (pt in points) {
                                    var closestBtn: Int? = null
                                    var minDist = Float.MAX_VALUE
                                    for ((btn, c) in centers) {
                                        val d = hypot(pt.x - c.x, pt.y - c.y)
                                        if (d <= hitRadius && d < minDist) {
                                            minDist = d
                                            closestBtn = btn
                                        }
                                    }
                                    if (closestBtn != null) hitSet.add(closestBtn)
                                }
                                return hitSet
                            }

                            do {
                                val event = awaitPointerEvent()
                                val activePoints = event.changes.filter { it.pressed }.map {
                                    it.consume()
                                    it.position
                                }
                                val newSet = if (activePoints.isEmpty()) {
                                    emptySet()
                                } else if (slideBetweenButtons || currentHeld.isEmpty()) {
                                    resolveButtonsAt(activePoints)
                                } else {
                                    currentHeld
                                }
                                if (newSet != currentHeld) {
                                    val toPress = newSet - currentHeld
                                    val toRelease = (currentHeld - newSet)
                                    currentHeld = newSet
                                    onButtonsChanged(toPress, toRelease)
                                }
                            } while (event.changes.any { it.pressed })

                            if (currentHeld.isNotEmpty()) {
                                onButtonsChanged(emptySet(), allAbxy)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        // Y - Top
        EditableControlContainer(
            elementId = ControlElementId.BTN_Y,
            transform = tY,
            alignment = Alignment.TopCenter,
            baseOffsetX = 0.dp,
            baseOffsetY = 0.dp,
            isEditorMode = isEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_Y,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            MonochromeGlyphButton(
                glyph = "Y",
                isPressed = gamepadState.isPressed(XboxButton.Y),
                modifier = Modifier.testTag("btn_y")
            )
        }
        // X - Left
        EditableControlContainer(
            elementId = ControlElementId.BTN_X,
            transform = tX,
            alignment = Alignment.CenterStart,
            baseOffsetX = 0.dp,
            baseOffsetY = 0.dp,
            isEditorMode = isEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_X,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            MonochromeGlyphButton(
                glyph = "X",
                isPressed = gamepadState.isPressed(XboxButton.X),
                modifier = Modifier.testTag("btn_x")
            )
        }
        // B - Right
        EditableControlContainer(
            elementId = ControlElementId.BTN_B,
            transform = tB,
            alignment = Alignment.CenterEnd,
            baseOffsetX = 0.dp,
            baseOffsetY = 0.dp,
            isEditorMode = isEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_B,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            MonochromeGlyphButton(
                glyph = "B",
                isPressed = gamepadState.isPressed(XboxButton.B),
                modifier = Modifier.testTag("btn_b")
            )
        }
        // A - Bottom
        EditableControlContainer(
            elementId = ControlElementId.BTN_A,
            transform = tA,
            alignment = Alignment.BottomCenter,
            baseOffsetX = 0.dp,
            baseOffsetY = 0.dp,
            isEditorMode = isEditorMode,
            isSelected = selectedElement == ControlElementId.BTN_A,
            onSelect = onSelectElement,
            onUpdateTransform = onUpdateTransform
        ) {
            MonochromeGlyphButton(
                glyph = "A",
                isPressed = gamepadState.isPressed(XboxButton.A),
                modifier = Modifier.testTag("btn_a")
            )
        }
    }
}

@Composable
private fun MonochromeGlyphButton(
    glyph: String,
    isPressed: Boolean,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "glyph_scale_$glyph"
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1.0f else 0.70f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "glyph_ring_$glyph"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(52.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = if (isPressed) {
                        listOf(Color.White, Color.White.copy(alpha = 0.80f))
                    } else {
                        listOf(Color(0x38000000), Color(0x1E000000))
                    }
                )
            )
            .border(
                width = if (isPressed) 2.4.dp else 1.4.dp,
                color = Color.White.copy(alpha = ringAlpha),
                shape = CircleShape
            )
    ) {
        // Subtle inner ring for depth
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .border(
                    width = 0.6.dp,
                    color = if (isPressed) Color.Black.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.22f),
                    shape = CircleShape
                )
        ) {
            Text(
                text = glyph,
                color = if (isPressed) Color.Black else Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/**
 * 8-Way Crystal D-Pad in Black & White monochrome with directional chevron indicators and smooth press animation.
 */
@Composable
private fun EightWayDpadCluster(
    gamepadState: GamepadState,
    slideBetweenButtons: Boolean,
    sizeDp: Dp,
    isEditorMode: Boolean,
    onDpadChanged: (pressed: Set<Int>, released: Set<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    val allDpad = remember {
        setOf(XboxButton.DPAD_UP, XboxButton.DPAD_DOWN, XboxButton.DPAD_LEFT, XboxButton.DPAD_RIGHT)
    }

    val up = gamepadState.isPressed(XboxButton.DPAD_UP)
    val down = gamepadState.isPressed(XboxButton.DPAD_DOWN)
    val left = gamepadState.isPressed(XboxButton.DPAD_LEFT)
    val right = gamepadState.isPressed(XboxButton.DPAD_RIGHT)
    val anyPressed = up || down || left || right

    val scale by animateFloatAsState(
        targetValue = if (anyPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "dpad_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp)
            .scale(scale)
            .then(
                if (!isEditorMode) {
                    Modifier.pointerInput(slideBetweenButtons) {
                        awaitEachGesture {
                            var currentSet = emptySet<Int>()

                            fun resolveDpad(pt: Offset): Set<Int> {
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dx = pt.x - cx
                                val dy = pt.y - cy
                                val dist = hypot(dx, dy)
                                if (dist < size.width * 0.12f) return emptySet()
                                val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                val out = mutableSetOf<Int>()
                                if (angleDeg in -157.5f..-22.5f) out.add(XboxButton.DPAD_UP)
                                if (angleDeg in 22.5f..157.5f) out.add(XboxButton.DPAD_DOWN)
                                if (angleDeg >= 112.5f || angleDeg <= -112.5f) out.add(XboxButton.DPAD_LEFT)
                                if (angleDeg in -67.5f..67.5f) out.add(XboxButton.DPAD_RIGHT)
                                return out
                            }

                            do {
                                val ev = awaitPointerEvent()
                                val active = ev.changes.firstOrNull { it.pressed }
                                val nextSet = if (active != null) {
                                    active.consume()
                                    if (slideBetweenButtons || currentSet.isEmpty()) {
                                        resolveDpad(active.position)
                                    } else {
                                        currentSet
                                    }
                                } else {
                                    emptySet()
                                }
                                if (nextSet != currentSet) {
                                    onDpadChanged(nextSet - currentSet, currentSet - nextSet)
                                    currentSet = nextSet
                                }
                            } while (ev.changes.any { it.pressed })

                            if (currentSet.isNotEmpty()) {
                                onDpadChanged(emptySet(), allDpad)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val arm = w * 0.33f
            val leftX = (w - arm) / 2f
            val topY = (h - arm) / 2f

            // Crystal outer ring
            drawCircle(
                color = CrystalIdleFill,
                radius = w * 0.48f
            )
            drawCircle(
                color = if (anyPressed) Color.White else CrystalRingIdle,
                radius = w * 0.48f,
                style = Stroke(width = if (anyPressed) 2.dp.toPx() else 1.2.dp.toPx())
            )

            // Cross outline
            drawRoundRect(
                color = Color(0x30000000),
                topLeft = Offset(leftX, w * 0.07f),
                size = Size(arm, h * 0.86f),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            drawRoundRect(
                color = Color(0x30000000),
                topLeft = Offset(w * 0.07f, topY),
                size = Size(w * 0.86f, arm),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
            drawRoundRect(
                color = CrystalRingSubtle,
                topLeft = Offset(leftX, w * 0.07f),
                size = Size(arm, h * 0.86f),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
            drawRoundRect(
                color = CrystalRingSubtle,
                topLeft = Offset(w * 0.07f, topY),
                size = Size(w * 0.86f, arm),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Active White Directional Highlights
            if (up) {
                drawRoundRect(
                    color = CrystalPressedFill,
                    topLeft = Offset(leftX, w * 0.07f),
                    size = Size(arm, h * 0.34f),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
            if (down) {
                drawRoundRect(
                    color = CrystalPressedFill,
                    topLeft = Offset(leftX, h * 0.59f),
                    size = Size(arm, h * 0.34f),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
            if (left) {
                drawRoundRect(
                    color = CrystalPressedFill,
                    topLeft = Offset(w * 0.07f, topY),
                    size = Size(w * 0.34f, arm),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
            if (right) {
                drawRoundRect(
                    color = CrystalPressedFill,
                    topLeft = Offset(w * 0.59f, topY),
                    size = Size(w * 0.34f, arm),
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }

            // Center pivot circle
            drawCircle(
                color = Color.White.copy(alpha = 0.25f),
                radius = w * 0.07f,
                center = Offset(w / 2f, h / 2f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Ultra-Clear Monochrome Virtual Thumbstick with smooth animated spring return and crisp white ring.
 */
@Composable
private fun VirtualThumbstick(
    label: String,
    stickMode: StickMode,
    doubleTapStickClick: Boolean,
    isStickClicked: Boolean,
    sizeDp: Dp,
    isEditorMode: Boolean,
    onStickVector: (Float, Float) -> Unit,
    onDoubleTapClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var knobX by remember { mutableFloatStateOf(0f) }
    var knobY by remember { mutableFloatStateOf(0f) }
    var originX by remember { mutableFloatStateOf(0f) }
    var originY by remember { mutableFloatStateOf(0f) }
    var isTouching by remember { mutableStateOf(false) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }

    val animKnobX by animateFloatAsState(
        targetValue = knobX,
        animationSpec = spring(stiffness = if (isTouching) Spring.StiffnessHigh else Spring.StiffnessMediumLow),
        label = "stick_x_$label"
    )
    val animKnobY by animateFloatAsState(
        targetValue = knobY,
        animationSpec = spring(stiffness = if (isTouching) Spring.StiffnessHigh else Spring.StiffnessMediumLow),
        label = "stick_y_$label"
    )
    val capScale by animateFloatAsState(
        targetValue = if (isTouching || isStickClicked) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "stick_cap_$label"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp)
            .then(
                if (!isEditorMode) {
                    Modifier.pointerInput(stickMode, doubleTapStickClick) {
                        awaitEachGesture {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val maxRadius = (size.width / 2f) * 0.72f

                            val firstDown = awaitPointerEvent().changes.firstOrNull { it.pressed }
                            if (firstDown != null) {
                                firstDown.consume()
                                val now = System.currentTimeMillis()
                                val isDoubleTap = doubleTapStickClick && (now - lastTapTimeMs) < 280L
                                lastTapTimeMs = now
                                if (isDoubleTap) {
                                    onDoubleTapClick(true)
                                }

                                isTouching = true
                                if (stickMode == StickMode.FLOATING) {
                                    originX = firstDown.position.x.coerceIn(cx * 0.45f, cx * 1.55f)
                                    originY = firstDown.position.y.coerceIn(cy * 0.45f, cy * 1.55f)
                                } else {
                                    originX = cx
                                    originY = cy
                                }

                                do {
                                    val ev = awaitPointerEvent()
                                    val active = ev.changes.firstOrNull { it.id == firstDown.id && it.pressed }
                                        ?: ev.changes.firstOrNull { it.pressed }
                                    if (active != null) {
                                        active.consume()
                                        val dx = active.position.x - originX
                                        val dy = active.position.y - originY
                                        val dist = hypot(dx, dy)
                                        val clamped = dist.coerceAtMost(maxRadius)
                                        val angle = atan2(dy, dx)
                                        knobX = if (dist > 0f) cos(angle) * (clamped / maxRadius) else 0f
                                        knobY = if (dist > 0f) sin(angle) * (clamped / maxRadius) else 0f
                                        onStickVector(knobX, knobY)
                                    }
                                } while (ev.changes.any { it.pressed })

                                isTouching = false
                                knobX = 0f
                                knobY = 0f
                                onStickVector(0f, 0f)
                                if (isDoubleTap) {
                                    onDoubleTapClick(false)
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseCenter = if (isTouching && stickMode == StickMode.FLOATING) {
                Offset(originX, originY)
            } else {
                Offset(size.width / 2f, size.height / 2f)
            }
            val outerRadius = (size.width / 2f) * 0.84f
            val maxTravel = (size.width / 2f) * 0.52f

            // Crystal-clear outer ring
            drawCircle(
                color = CrystalIdleFill,
                radius = outerRadius,
                center = baseCenter
            )
            drawCircle(
                color = if (isTouching || isStickClicked) Color.White else CrystalRingIdle,
                radius = outerRadius,
                center = baseCenter,
                style = Stroke(width = if (isTouching) 2.2.dp.toPx() else 1.2.dp.toPx())
            )

            // Subtle inner precision ticks
            drawLine(
                color = Color.White.copy(alpha = 0.24f),
                start = Offset(baseCenter.x - outerRadius * 0.55f, baseCenter.y),
                end = Offset(baseCenter.x + outerRadius * 0.55f, baseCenter.y),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White.copy(alpha = 0.24f),
                start = Offset(baseCenter.x, baseCenter.y - outerRadius * 0.55f),
                end = Offset(baseCenter.x, baseCenter.y + outerRadius * 0.55f),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )

            val capCenter = Offset(
                x = baseCenter.x + animKnobX * maxTravel,
                y = baseCenter.y + animKnobY * maxTravel
            )
            val capRadius = outerRadius * 0.42f * capScale

            // Connection stem when deflected
            if (isTouching) {
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = baseCenter,
                    end = capCenter,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Thumbstick cap
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isTouching || isStickClicked) {
                        listOf(Color.White, Color.White.copy(alpha = 0.65f))
                    } else {
                        listOf(Color(0x55FFFFFF), Color(0x28000000))
                    },
                    center = capCenter,
                    radius = capRadius
                ),
                radius = capRadius,
                center = capCenter
            )
            drawCircle(
                color = Color.White,
                radius = capRadius,
                center = capCenter,
                style = Stroke(width = if (isTouching) 2.2.dp.toPx() else 1.5.dp.toPx())
            )
        }
        Text(
            text = label,
            color = if (isTouching || isStickClicked) Color.Black else Color.White.copy(alpha = 0.78f),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Floating Black & White HUD Editor Panel allowing the user to tap ANY button, drag it anywhere,
 * resize it (with slider or +/- buttons), adjust crystal clarity/opacity, or apply globally.
 */
@Composable
private fun LayoutEditorToolbar(
    selectedElement: ControlElementId,
    transform: ControlTransform,
    onTransformChanged: (ControlTransform) -> Unit,
    onApplyToAll: (scale: Float, opacity: Float) -> Unit,
    onResetAll: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianCard.copy(alpha = 0.94f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .padding(top = 6.dp)
            .width(344.dp)
            .border(1.5.dp, Color.White, RoundedCornerShape(20.dp))
            .testTag("layout_editor_toolbar")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GAMEPAD HUD EDITOR",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Selected: ${selectedElement.displayName} (Tap & Drag Any Button)",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
                IconButton(
                    onClick = onResetAll,
                    modifier = Modifier.size(32.dp).testTag("btn_reset_layout")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset HUD", tint = TextSecondary)
                }
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.testTag("btn_done_layout")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Save HUD", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(4.dp))

            // Size Slider + Precision Step Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "Size %.0f%%", transform.scale * 100),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(66.dp)
                )
                IconButton(
                    onClick = {
                        onTransformChanged(
                            transform.copy(scale = (transform.scale - 0.05f).coerceIn(0.50f, 1.80f))
                        )
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Smaller", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Slider(
                    value = transform.scale,
                    onValueChange = { onTransformChanged(transform.copy(scale = it)) },
                    valueRange = 0.50f..1.80f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        onTransformChanged(
                            transform.copy(scale = (transform.scale + 0.05f).coerceIn(0.50f, 1.80f))
                        )
                    },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Larger", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            // Crystal Clarity / Opacity Slider
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "Clear %.0f%%", transform.opacity * 100),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(66.dp)
                )
                Slider(
                    value = transform.opacity,
                    onValueChange = { onTransformChanged(transform.copy(opacity = it)) },
                    valueRange = 0.15f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Apply All",
                    color = Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable { onApplyToAll(transform.scale, transform.opacity) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
