# Prompy v4.5 Stable - Release Notes

## 🚀 New Features & Architecture
- **Advanced Settings Screen**: Consolidated all system security and data management options into a dedicated screen for a cleaner, less overwhelming Settings experience.
- **Full Biometric Integration**: The Secure Vault now supports native Android Fingerprint/Face Unlock. If biometric authentication is cancelled or fails, the app seamlessly falls back to your 4-digit PIN.
- **Native Web-to-OS Bridge**: Implemented a robust communication layer between the web UI and Android OS for handling system-level tasks like browser redirects and security protocols.
- **And More!**

## ✨ UI Polish & Experience
- **Fluid Animation System**: 
    - **Staggered Entry**: Cards and search results now slide into view with a timed stagger effect.
    - **Layered Screen Transitions**: New screens now feature "depth-aware" transitions where headers, bodies, and action bars enter at slightly different speeds.
    - **Micro-Interactions**: Improved tactile feedback on buttons, FABs, and switches with refined scale-down animations.
- **Dynamic Wordmark**: Added a smooth entrance animation for the Prompy branding on launch.
- **Responsive Color Picker**: Redesigned the theme color picker footer to prevent clipping and layout breaks on narrow/small mobile screens.
- **System Browser Support**: External links (like GitHub) now open in your device's native browser instead of hijacking the app view.

## 🛠 Bug Fixes & Stability
- **Navigation Engine Overhaul**: Fixed a critical bug where the device's physical back button would occasionally close the app instead of returning to the previous screen.
- **Toggle Logic Fix**: Resolved an issue where settings toggles were unresponsive or required multiple taps to trigger.
- **HTML Structure Validation**: Cleaned up legacy code, resolved duplicate element IDs, and fixed unclosed tags to ensure smoother rendering performance.
- **State Persistence**: Improved reliability of theme and security settings saved across app restarts.
- **Safe ZIP Import/Export**: Enhanced the prompts backup system to correctly distinguish between normal and secure vault prompts during restoration.

---