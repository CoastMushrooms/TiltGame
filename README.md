# Dead Peek - Android Zombie Shooter

Dead Peek is an arcade-style survival game for Android where the player peeks out from a barricade against advancing zombie hordes. The game utilizes a unique peeking mechanic where the player must tilt the device (or use keyboard controls) to aim and fire from behind cover.

## Game Mechanics
* **Peeking:** The player is safe behind a central wall. You must tilt or press keys to peek out from the left or right side to get a clear shot.
* **Zombie Types:** Features standard zombies, armored zombies that require more hits, and slow-moving Bosses with high health.
* **Progression:** The game includes 6 hand-crafted levels with increasing difficulty, followed by a procedural endless mode.
* **Audio:** Dynamic sound effects for gunshots and reloads, alongside a music system that transitions between calm and intense tracks based on the action.

## Controls
* **Mobile:** Tilt the phone left or right to peek; tap the screen to shoot.
* **PC/Emulator:** Use A/Left Arrow to peek left, D/Right Arrow to peek right, and Left Mouse Click to shoot.
* **Reloading:** The weapon reloads automatically when the magazine is empty.

## Prerequisites
* Java 17
* Android SDK (API Level 33)
* Git

## Getting Started

### 1. Clone the repository
git clone https://github.com/CoastMushrooms/TiltGame.git
cd TiltGame

### 2. Build the APK
Run the Gradle wrapper to compile the project:
./gradlew assembleDebug

### 3. Locate the APK
After a successful build, the APK file can be found at:
app/build/outputs/apk/debug/app-debug.apk

## Installation
To install the game directly on an Android device:
1. Transfer the app-debug.apk file to your device.
2. Open the file to install. This requires "Install from Unknown Sources" to be enabled in your Android settings.

## License
Refer to the LICENSE file for more information.