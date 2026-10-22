package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NeonAzure
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.PsCircleRed
import com.example.ui.theme.PsCrossBlue
import com.example.ui.theme.PsSquarePink
import com.example.ui.theme.PsTriangleGreen
import com.example.ui.theme.TurboAmber

/**
 * PlayStation Controller Buttons mapped to the W3C / Windows Standard 16-Button Gamepad layout:
 * B0=Cross, B1=Circle, B2=Square, B3=Triangle,
 * B4=L1, B5=R1, B6=L2, B7=R2,
 * B8=Share, B9=Options, B10=L3, B11=R3,
 * B12=D-Pad Up, B13=D-Pad Down, B14=D-Pad Left, B15=D-Pad Right
 */
enum class PsButton(
    val id: String,
    val label: String,
    val symbol: String,
    val defaultHidBit: Int,
    val defaultPcKey: String,
    val accentColor: Color
) {
    CROSS("CROSS", "Cross", "✕", 0, "Space / A (B0)", PsCrossBlue),
    CIRCLE("CIRCLE", "Circle", "◯", 1, "Left Ctrl / B (B1)", PsCircleRed),
    SQUARE("SQUARE", "Square", "▢", 2, "R / X (B2)", PsSquarePink),
    TRIANGLE("TRIANGLE", "Triangle", "△", 3, "F / Y (B3)", PsTriangleGreen),
    L1("L1", "L1 Bumper", "L1", 4, "Q / LB (B4)", NeonCyan),
    R1("R1", "R1 Bumper", "R1", 5, "E / RB (B5)", NeonCyan),
    L2_BTN("L2_BTN", "L2 Trigger", "L2", 6, "Right Click / LT (B6)", NeonAzure),
    R2_BTN("R2_BTN", "R2 Trigger", "R2", 7, "Left Click / RT (B7)", NeonAzure),
    SHARE("SHARE", "Create / Share", "SHARE", 8, "Tab / View (B8)", NeonCyan),
    OPTIONS("OPTIONS", "Options", "OPT", 9, "Esc / Menu (B9)", NeonCyan),
    L3("L3", "Left Stick Click", "L3", 10, "Shift / LS (B10)", NeonCyan),
    R3("R3", "Right Stick Click", "R3", 11, "V / RS (B11)", NeonCyan),
    DPAD_UP("DPAD_UP", "D-Pad Up", "▲", 12, "Up Arrow (B12)", NeonCyan),
    DPAD_DOWN("DPAD_DOWN", "D-Pad Down", "▼", 13, "Down Arrow (B13)", NeonCyan),
    DPAD_LEFT("DPAD_LEFT", "D-Pad Left", "◀", 14, "Left Arrow (B14)", NeonCyan),
    DPAD_RIGHT("DPAD_RIGHT", "D-Pad Right", "▶", 15, "Right Arrow (B15)", NeonCyan),
    PS_HOME("PS_HOME", "PS Home", "PS", 9, "Enter / Guide", TurboAmber),
    TOUCHPAD("TOUCHPAD", "Touchpad Click", "TPAD", 8, "M / Map", NeonAzure)
}

enum class ControlClusterId(val title: String) {
    LEFT_SHOULDERS("L1 / L2 Triggers"),
    RIGHT_SHOULDERS("R1 / R2 Triggers"),
    DPAD("Directional D-Pad"),
    ACTION_BUTTONS("PS Action Cluster (✕◯▢△)"),
    LEFT_STICK("Left Analog Stick (L3)"),
    RIGHT_STICK("Right Analog Stick (R3)"),
    CENTER_DECK("Center Console Deck")
}

data class ClusterTransform(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1.0f
)

enum class LayoutPreset(val displayName: String) {
    PS5_DUALSENSE("DualSense Standard"),
    FPS_COMPETITIVE("FPS Claw / Enlarged Sticks"),
    RACING_SIM("Racing Pedals & Gyro"),
    ARCADE_FIGHTER("Arcade Enlarged Action")
}

enum class HapticIntensity(
    val label: String,
    val durationMs: Long,
    val amplitude: Int
) {
    OFF("Muted (0%)", 0L, 0),
    LIGHT("Light Tick (12ms)", 12L, 90),
    MEDIUM("Tactile Pulse (22ms)", 22L, 165),
    HEAVY("Console Thump (38ms)", 38L, 255),
    CUSTOM("Custom Tuned", 28L, 200)
}

data class MacroCombo(
    val id: String,
    val name: String,
    val buttons: List<PsButton>,
    val stepDelayMs: Long = 70L
)

enum class ActiveModal {
    NONE,
    CONNECTION_HUB,
    BUTTON_MAPPER,
    TURBO_MACRO_DECK,
    HAPTIC_GYRO_SETTINGS
}

data class ButtonMappingEntry(
    val button: PsButton,
    val mappedHidBit: Int,
    val mappedPcKey: String
)
