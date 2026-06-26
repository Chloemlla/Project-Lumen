# Project-Lumen

Flutter app foundation for reminder, break recovery, pomodoro, statistics,
settings, template management, and Windows desktop delivery.

## Platform Support

- Android and iOS mobile foundations.
- Windows desktop runner committed under `windows/`.
- Windows persistence uses `sqflite_common_ffi` with bundled sqlite libraries.
- CI builds Windows directly from the committed runner instead of recreating it
  during the workflow.

## Windows Build

```powershell
flutter config --enable-windows-desktop
flutter pub get
flutter build windows --release
```

## Getting Started

This project is a starting point for a Flutter application.

A few resources to get you started if this is your first Flutter project:

- [Learn Flutter](https://docs.flutter.dev/get-started/learn-flutter)
- [Write your first Flutter app](https://docs.flutter.dev/get-started/codelab)
- [Flutter learning resources](https://docs.flutter.dev/reference/learning-resources)

For help getting started with Flutter development, view the
[online documentation](https://docs.flutter.dev/), which offers tutorials,
samples, guidance on mobile development, and a full API reference.
