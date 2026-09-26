# CDR Analyzer Android

Native Android CDR analysis application. This is not a WebView and does not load the ClearExams CDR website.

## v1.0 foundation
- Kotlin + Jetpack Compose native UI
- Android document picker
- Local XLS/XLSX parsing
- Basic call/contact summaries
- Native tabs for Calls, Contacts, Devices, Towers, Movement and Notes
- GitHub Actions builds APK and AAB

CDR files are processed locally. Future modules will add robust telecom-column mapping, CSV import, Room case storage, tagging, IMEI/IMSI transitions, tower analysis, movement mapping, investigation timelines, reports and exports.

## Build
Push to `main` or run the **Build Android APK and AAB** workflow. Download `CDR-Analyzer-APK` from the workflow artifacts for direct Android installation.
