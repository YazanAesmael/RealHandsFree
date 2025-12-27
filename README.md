# RealHandsFree 👋📱

**RealHandsFree** is a native Android accessibility application that allows users to control their device entirely without touch. By leveraging Google MediaPipe for high-performance hand tracking and Android Accessibility Services, this app converts hand gestures into system-wide clicks and scrolls.

## ✨ Features

* **Touch-Free Navigation:** Move the cursor by moving your hand in front of the front-facing camera.
* **Gesture Recognition:**
* **Pinch (Thumb + Index):** Performs a **Click**.
* **Pinch & Drag:** Performs a **Scroll/Swipe**.


* **Visual Feedback:**
* **Cursor:** A red dot indicates your interaction point (turns green when pinching).
* **Skeleton Overlay:** Real-time visualization of hand landmarks for debugging and feedback.


* **System-Wide Control:** Works over any app (YouTube, Chrome, Instagram, etc.) using the Accessibility Service API.
* **Modern Stack:** Built with **Kotlin**, **Jetpack Compose**, and **CameraX**.

## 🚀 How to Build & Run

### Prerequisites

* Android Studio (Ladybug or newer recommended).
* Android Device running **Android 8.0 (API 26)** or higher.
* **Note:** This app requires a physical device with a front camera; it will not function correctly on standard emulators.

### Installation

1. **Clone the repository**:
```bash
git clone https://github.com/YazanAesmael/RealHandsFree.git

```


2. **Open in Android Studio**.
3. **Sync Gradle**:
* *Important:* The `build.gradle.kts` file contains a task `downloadModel` that automatically fetches the `hand_landmarker.task` file from Google Storage and places it in your assets folder. Ensure you have an internet connection during the first sync.


4. **Run the App**: Select `app` and run it on your physical device.

## 🛠 Setup & Permissions

Upon launching the app, you will be greeted by a Setup Screen. You must grant permissions in this specific order:

1. **Camera Access:** Required to track hand movements.
2. **Display Over Other Apps:** Required to draw the cursor (red dot) and skeleton overlay on top of other applications.
3. **Accessibility Service:** Required to perform programmatic clicks and scrolls. The app will redirect you to Settings; find **"Real Hands Free Setup"** (or similar based on service name) and enable it.

## 🎮 Usage Guide

Once the service is active, you can close the main app. The camera runs in the background.

| Gesture | Action | Visual Cue |
| --- | --- | --- |
| **Open Hand** | **Move Cursor** | Red Dot follows index finger |
| **Quick Pinch** (Thumb + Index) | **Click / Tap** | Dot turns Green momentarily |
| **Pinch + Move Hand** | **Scroll** | Dot turns Green + Screen swipes |

## 🏗 Architecture & Tech Stack

The application is modularized into three core components:

### 1. The Setup UI (`MainActivity.kt`)

* Built with **Jetpack Compose**.
* Manages the permission lifecycle and guides the user through the 3-step setup process.
* Detects if the Accessibility Service is currently enabled using `AccessibilityManager`.

### 2. The Brain (`HandTrackingManager.kt`)

* **CameraX:** Captures frames from the front camera using `ImageAnalysis`.
* **MediaPipe:** Runs the `HandLandmarker` model on the GPU.
* **Logic:**
* Calculates the distance between the Index Tip and Thumb Tip to detect "Pinch".
* Distinguishes between a **Click** (small movement during pinch) and a **Scroll** (large movement during pinch).
* Applies smoothing algorithms to the cursor coordinates to reduce jitter.



### 3. The Enforcer (`HandsFreeAccessibilityService.kt`)

* Extends `AccessibilityService`.
* **CursorOverlay:** Manages two `WindowManager` views:
* *CursorView:* The small interaction dot.
* *SkeletonView:* The full-screen debug overlay.


* **Action Dispatch:** Uses `dispatchGesture` to inject Click and Scroll events into the Android system.
* **"Step Aside" Logic:** Before dispatching a click, the service momentarily shrinks the overlay windows (0x0 size) to ensure the click passes through to the target app underneath, rather than clicking the overlay itself.

## 📦 Project Structure

```text
com.yaxan.realhandsfree
├── MainActivity.kt              // Compose UI for Permissions
├── CursorOverlay.kt             // Draws Cursor & Skeleton on WindowManager
├── service
│   └── HandsFreeAccessibilityService.kt  // System Integration & Event Dispatch
└── tracking
    └── HandTrackingManager.kt   // CameraX & MediaPipe Logic

```

## ⚠️ Known Issues / Limitations

* **Lighting:** Hand tracking relies on the camera; performance may drop in low-light environments.
* **Orientation:** Currently optimized for Portrait mode.
* **Security:** Android disables Accessibility clicks on sensitive screens (like system permission dialogs or banking apps) for security reasons.

## 📄 License

[MIT License](https://www.google.com/search?q=LICENSE)