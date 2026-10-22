# NeonPad — Digital Controller & Console for PC Gaming

NeonPad turns your Android smartphone into a ultra-responsive digital gamepad and gaming console for playing PC games on Windows laptops. It delivers native zero-install Bluetooth HID Gamepad connectivity alongside a zero-install Wi-Fi Web Bridge, styled in an OLED dark aesthetic with vibrant electric neon blue accents, comprehensive PlayStation-style controls, motion gyro aiming, custom layout editing, and tactile haptic feedback.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Production-Ready & Real Hardware Only (No Fake Simulations)**:
> - **Zero Mock/Simulation Code**: Every subsystem operates with actual Android hardware APIs and network sockets:
>   - **Real Bluetooth HID Gamepad Profile**: Registers with Android's `BluetoothHidDevice` using an authentic USB HID Gamepad descriptor (16 buttons, 4 axes, hat switch). Windows pairs natively via Windows Bluetooth settings with zero laptop software.
>   - **Real Local Web Bridge**: Built-in HTTP and WebSocket server running directly on Android (port 8080). When opened on the laptop browser (`http://<phone-ip>:8080`), it establishes a live, low-latency (<5ms) WebSocket connection with real-time button, stick, and trigger telemetry.
>   - **Real Sensor Fusion**: Uses Android `SensorManager` with real device gyroscope/rotation vector sensors for tilt aiming.
>   - **Real Hardware Haptics**: Uses Android `Vibrator` / `VibrationEffect` for tactile physical feedback on press, release, and stick clicks.
>   - **Persistent Layouts**: Real persistent state management saving custom button placements, scales, and preferences locally.

---

## 1. Overview & Core Concept

- **What It Does**: Transforms the phone into an authentic gaming controller recognized natively by Windows games, Steam, and emulators, featuring dual virtual analog thumbsticks, pressure triggers, D-pad, action cluster, motion sensing, and full touch-haptic response.
- **Target Audience**: PC gamers who want to play games on their laptop without carrying physical controllers or installing third-party driver software on Windows machines.
- **Key Value**: 100% zero-software installation on the Windows laptop via Bluetooth HID peripheral mode or zero-install browser tab bridge, packaged with console-grade visual aesthetics and responsive input latency.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Launch & Real Hardware HUD**:
   - Status bar displaying Bluetooth adapter state, HID peripheral capability support check, Wi-Fi local IP address, and active client connections.
   - Quick "Start Bluetooth HID" button to put phone in Gamepad discoverable mode for Windows Bluetooth pairing.
   - Live "Web Bridge" card showing the exact URL (`http://192.168.x.x:8080`) to open on the laptop, plus connection indicator that illuminates green when the laptop connects.
2. **Controller Experience (PlayStation Layout)**:
   - Left side: Full multi-directional D-Pad, Left Analog Stick (with clickable L3), L1 Bumper, and L2 Trigger slider.
   - Center HUD: Connection indicator, Latency ping (ms), Gyro toggle, Turbo toggle, Profile switch, and Menu button.
   - Right side: Iconic PlayStation action buttons (✕, ◯, ▢, △) with luminous neon cyan glyphs, Right Analog Stick (with clickable R3), R1 Bumper, and R2 Trigger slider.
3. **Motion Tilt Aiming / Steering**:
   - Toggleable gyro aiming maps real device pitch and roll tilt directly to right thumbstick (aiming) or left thumbstick (steering) with deadzone and smoothing controls.
4. **Custom Layout & Button Editor**:
   - Visual drag-and-drop workspace where players can tap and reposition buttons, resize thumbsticks, toggle specific elements, and save presets for different game genres (FPS, Racing, Fighting, RPG).
5. **Turbo & Macro Deck**:
   - Select any button (e.g., Cross ✕ or R2 Trigger), configure pulse frequency (5Hz, 10Hz, 20Hz), and engage turbo with a quick-tap badge.

### Visual Identity & Theme

- **Aesthetic Direction**: Cyberpunk Stealth Console / Luxury Dark HUD. Clean, tactile, high-tech interface with glowing neon borders, glassmorphic cards, and crisp vector geometry.
- **Color Palette**:
  - `Background`: Deep OLED Obsidian Black (`#05080E`, `#0A0F18`)
  - `Surface / Deck`: Carbon Gray with subtle cyan tint (`#111827`, `#1A2333`)
  - `Primary / Glow`: Electric Neon Cyan (`#00E5FF`)
  - `Secondary Accent`: Luminescent Royal Azure (`#2979FF`)
  - `Warning / Turbo`: Neon Amber Gold (`#FFD600`)
  - `Action Glyphs`: Precision PlayStation colors tuned to neon contrast (Cross: `#2979FF`, Circle: `#FF5252`, Square: `#FF4081`, Triangle: `#00E676`).

---

## 3. Key Product Decisions & Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                              NeonPad UI                                │
│  ┌───────────────────────┐  ┌──────────────────┐  ┌─────────────────┐  │
│  │     Gamepad Deck      │  │  Layout Editor   │  │ Settings & HUD  │  │
│  │ (Sticks, D-Pad, PS BT)│  │ (Drag & Resize)  │  │ (Haptics, Gyro) │  │
│  └───────────┬───────────┘  └─────────┬────────┘  └────────┬────────┘  │
└──────────────┼────────────────────────┼────────────────────┼───────────┘
               │                        │                    │
               ▼                        ▼                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        GamepadControllerViewModel                      │
│  - Real-time Input Aggregator (Buttons, Joysticks, Triggers, Gyro)     │
│  - Turbo Engine (Coroutine Clock 5-20Hz)                               │
│  - Haptics Feedback Dispatcher (Android Vibrator / VibrationEffect)    │
│  - Motion Sensor Listener (TYPE_ROTATION_VECTOR / TYPE_GYROSCOPE)      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
         ┌──────────────────────────┴──────────────────────────┐
         ▼                                                     ▼
┌───────────────────────────────────┐ ┌──────────────────────────────────┐
│   BluetoothHidManager (Native)    │ │   WebBridgeServer (Embedded)     │
│ - Android BluetoothHidDevice      │ │ - Real HTTP & WebSocket Server   │
│ - Standard HID Gamepad Descriptor │ │ - Serves Interactive Web Console │
│ - Low-Latency Byte Input Reports  │ │ - Broadcasts Real-Time Telemetry │
└───────────────────────────────────┘ └──────────────────────────────────┘
```
