# Prompy v5.0 Stable - Release Notes

## 🚀 Native Storage & Performance
- **Unlimited Prompt Storage**: Moved away from restricted browser storage to a high-performance **Android Filesystem Bridge**. You can now save thousands of prompts and high-resolution reference images without hitting the previous 5MB limit.
- **Pulsing Loading Screen**: Added a professional startup sequence with a pulsing logo and progress bar to ensure the app UI and filesystem are fully synced before interaction.
- **Seamless Local Migration**: Integrated an automatic migration engine that safely moves your existing prompts from legacy storage to the new filesystem upon first launch.

## 🖼 Adaptive Media Engine
- **Intelligent Aspect Ratios**: Prompt thumbnails no longer force images into squares. The UI now dynamically adapts to the natural aspect ratio of your AI-generated art, supporting portrait, landscape, and panoramic views.
- **Enhanced Gallery View**: Redesigned the prompt detail screen with a `max-height` safety layer, ensuring tall images fill the screen beautifully while keeping text and buttons perfectly accessible.
- **Stacked Photo Effects**: Added a visual "stack" indicator for prompts containing multiple reference images.

## 🛡 Advanced Selection & Workflow
- **Power-User Selection Mode**: Enter a new bulk-editing mode via long-press. Support for **Multi-Select Export, Multi-Select Cloud Upload, and Multi-Select Delete**.
- **Context-Aware Back Navigation**: Pressing the Android back button while selecting items now intelligently exits selection mode first, preventing accidental app closures.
- **Vault UI Overhaul**: Moved Search and Filter tools directly into the main Vault header for immediate access, reducing the steps needed to find secure prompts.

## 🎨 Professional Personalization
- **AMOLED Dark Mode**: Added a true-black theme option specifically optimized for OLED screens to save battery and provide ultimate contrast.
- **HSV Canvas Color Picker**: Upgraded the customization engine with a full saturation/value canvas and hue slider, allowing for infinite precision in setting your app's accent color.
- **Rich Formatting Modals**: System alerts and confirmation dialogs now support bold HTML formatting for clearer warnings and instructions.

## ☁ Cloud Sync 2.0
- **Smart Cloud Routing**: The Backendless engine now automatically organizes prompts into `normal` and `vault` subfolders on the server.
- **Collision Resolution**: "Update Feed" now intelligently detects if a prompt has been moved between the vault and the dashboard on another device and updates your local library to match.
- **Real-Time Sync Console**: Added a detailed technical console in Advanced Settings to track every step of the upload/download process with status logs.
