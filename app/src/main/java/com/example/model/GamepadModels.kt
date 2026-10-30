package com.example.model

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Standard W3C Gamepad 17-button indices used by Xbox Cloud Gaming.
 */
object XboxButton {
    const val A = 0
    const val B = 1
    const val X = 2
    const val Y = 3
    const val LB = 4
    const val RB = 5
    const val LT = 6
    const val RT = 7
    const val VIEW = 8
    const val MENU = 9
    const val L3 = 10
    const val R3 = 11
    const val DPAD_UP = 12
    const val DPAD_DOWN = 13
    const val DPAD_LEFT = 14
    const val DPAD_RIGHT = 15
    const val XBOX = 16
    const val COUNT = 17

    fun label(index: Int): String = when (index) {
        A -> "A"
        B -> "B"
        X -> "X"
        Y -> "Y"
        LB -> "LB"
        RB -> "RB"
        LT -> "LT"
        RT -> "RT"
        VIEW -> "View"
        MENU -> "Menu"
        L3 -> "L3"
        R3 -> "R3"
        DPAD_UP -> "Up"
        DPAD_DOWN -> "Down"
        DPAD_LEFT -> "Left"
        DPAD_RIGHT -> "Right"
        XBOX -> "Xbox"
        else -> "Btn$index"
    }
}

data class GamepadState(
    val buttonsMask: Int = 0,
    val lt: Float = 0f,
    val rt: Float = 0f,
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f
) {
    fun isPressed(buttonIndex: Int): Boolean {
        if (buttonIndex == XboxButton.LT) return lt > 0.12f || (buttonsMask and (1 shl XboxButton.LT)) != 0
        if (buttonIndex == XboxButton.RT) return rt > 0.12f || (buttonsMask and (1 shl XboxButton.RT)) != 0
        return (buttonsMask and (1 shl buttonIndex)) != 0
    }

    fun withButton(buttonIndex: Int, pressed: Boolean): GamepadState {
        val newMask = if (pressed) {
            buttonsMask or (1 shl buttonIndex)
        } else {
            buttonsMask and (1 shl buttonIndex).inv()
        }
        return copy(buttonsMask = newMask)
    }
}

enum class StickMode(val label: String) {
    FLOATING("Floating (Touch Origin)"),
    FIXED("Fixed Center")
}

enum class SensitivityCurve(val label: String, val exponent: Float) {
    LINEAR("Linear (1:1)", 1.0f),
    PRECISION("Precision (Fine Center)", 1.55f),
    AGGRESSIVE("Quick Response", 0.72f)
}

enum class GyroMode(val label: String) {
    OFF("Off"),
    WHILE_LT("While Holding LT (ADS)"),
    ALWAYS_ON("Always On")
}

enum class ControlElementId(val displayName: String) {
    BTN_LT("LT Analog Trigger"),
    BTN_LB("LB Shoulder Button"),
    BTN_RB("RB Shoulder Button"),
    BTN_RT("RT Analog Trigger"),
    LEFT_STICK("Left Thumbstick (LS)"),
    RIGHT_STICK("Right Thumbstick (RS)"),
    DPAD_CLUSTER("8-Way D-Pad"),
    ABXY_CLUSTER("ABXY Action Diamond"),
    BTN_A("A Button (Bottom)"),
    BTN_B("B Button (Right)"),
    BTN_X("X Button (Left)"),
    BTN_Y("Y Button (Top)"),
    BTN_L3("L3 Stick Click Button"),
    BTN_VIEW("View Button"),
    BTN_XBOX("Xbox Guide Button"),
    BTN_MENU("Menu Button"),
    BTN_R3("R3 Stick Click Button"),
    // Legacy group keys preserved for compatibility
    LEFT_TRIGGERS("Left Shoulder Group"),
    RIGHT_TRIGGERS("Right Shoulder Group"),
    BOTTOM_STRIP("Bottom System Strip")
}

data class ControlTransform(
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val scale: Float = 1.0f,
    val opacity: Float = 0.85f
)

data class ControllerProfile(
    val id: String = "default",
    val name: String = "Default Crystal Pad",
    val gameTitleMatch: String = "",
    val stickMode: StickMode = StickMode.FLOATING,
    val deadzone: Float = 0.08f,
    val sensitivity: Float = 1.0f,
    val sensitivityCurve: SensitivityCurve = SensitivityCurve.LINEAR,
    val slideBetweenButtons: Boolean = true,
    val doubleTapStickClick: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val gyroMode: GyroMode = GyroMode.OFF,
    val gyroSensitivity: Float = 1.2f,
    val portraitLayout: Map<ControlElementId, ControlTransform> = defaultControlMap(),
    val landscapeLayout: Map<ControlElementId, ControlTransform> = defaultControlMap()
) {
    fun processStickVector(rawX: Float, rawY: Float): Pair<Float, Float> {
        val mag = sqrt(rawX * rawX + rawY * rawY)
        if (mag <= deadzone || mag == 0f) return 0f to 0f
        val normalized = ((mag - deadzone) / (1f - deadzone)).coerceIn(0f, 1f)
        val curved = (normalized.pow(sensitivityCurve.exponent) * sensitivity).coerceIn(0f, 1f)
        val nx = (rawX / mag) * curved
        val ny = (rawY / mag) * curved
        return nx.coerceIn(-1f, 1f) to ny.coerceIn(-1f, 1f)
    }

    companion object {
        fun defaultControlMap(): Map<ControlElementId, ControlTransform> =
            ControlElementId.entries.associateWith { ControlTransform() }
    }
}

enum class TvAspectRatioMode(val key: String, val label: String, val description: String) {
    FIT("fit", "Fit (Letterbox / Pillarbox)", "Preserves exact aspect ratio with black bars; zero cropping or stretching"),
    FILL_16_9("fill_16_9", "16:9 Fill", "Fills standard 16:9 widescreen viewport"),
    RATIO_4_3("4_3", "4:3 Classic", "Constrains stream to 4:3 aspect ratio with black pillarbox bars"),
    INTEGER_SCALE("integer", "Integer Scale (Pixel-Perfect)", "Scales by exact integer multiple (1x/2x) inside TV physical resolution")
}

enum class UpscalerTier(val label: String) {
    OFF("Off"),
    BALANCED("Balanced (FSR 1.0)"),
    HIGH("High (3D Ultra Detail)"),
    AUTO("Auto (GPU Benchmark)")
}

data class ExternalDisplayInfo(
    val displayId: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRateHz: Int
) {
    val summary: String
        get() = "$name (${width}x${height} @ ${refreshRateHz}Hz)"
}

data class StreamEngineSettings(
    val targetResolution: String = "1080p_hq",
    val maxBitrateKbps: Int = 20000,
    val codecProfile: String = "high",
    val showStatsOverlay: Boolean = true,
    val clarityBoost: Int = 1,
    val videoSaturation: Int = 105,
    val videoContrast: Int = 103,
    val videoBrightness: Int = 100,
    val videoSharpness: Int = 0,
    val enableFsr: Boolean = false,
    val fsrSharpness: Int = 75,
    val upscalerTier: UpscalerTier = UpscalerTier.AUTO,
    val upscalerDetailStrength: Int = 40,
    val upscalerDeblockStrength: Int = 20,
    val upscalerGrainEnabled: Boolean = false,
    val upscalerTestPattern: Boolean = false,
    val upscalerOutputWidth: Int = 3840,
    val upscalerOutputHeight: Int = 2160,
    val audioBoostPercent: Int = 100,
    val disableIdleCheck: Boolean = true,
    val enableDashboardGamepadNav: Boolean = true,
    val tvAspectRatioMode: TvAspectRatioMode = TvAspectRatioMode.FIT
)

data class StreamTelemetry(
    val isStreaming: Boolean = false,
    val gameTitle: String = "",
    val fps: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val bitrateKbps: Int = 0,
    val rttMs: Int = 0,
    val decodeMs: Float = 0f,
    val framesDropped: Int = 0,
    val packetLossPct: Float = 0f,
    val detectedGamepads: Int = 1
)
