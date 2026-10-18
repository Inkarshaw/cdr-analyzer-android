# CDR Analyzer Android

Native Kotlin + Jetpack Compose companion to [ClearExams CDR Analysis](https://clearexams.ink/cdranalysis/).
The app parses imported XLS/XLSX/CSV files **on-device**. It does not embed the website in a WebView.

## Available in the Android app

- Dashboard, paginated Records and raw Excel View
- Advanced A/B-party, date/time, device, tower, provider and activity filters
- Duplicate-safe analysis switch (raw source records remain intact)
- **Contacts intelligence**: number/name/tag search; tag and call/SMS filters; filtered vs historical scope;
  first voice call from all matching imported dates even when date/time filters are active;
  date and volume sorting; incoming/outgoing/SMS totals; profile with tower summary and activity timeline;
  contacts seen by 2+ CDR subjects; contact selection; copy selected numeric numbers; CSV export; CDR request link
- Locations, Devices/IMEI/IMSI, SMS Intelligence, Incident Timeline, Patterns, Movement Map and Multi-number analysis
- Investigation Insights and Data Quality (missing fields, duplicates and geocoordinates)
- Cases / Workspace: local case storage, identities, common contacts, links, relationships,
  correlations, timeline, backups and reports

## Privacy and evidence handling

- Source CDR files and analyses are processed locally, not uploaded by the app.
- Clipboard copy is explicitly initiated, but copied numbers can be accessed by the system clipboard;
  clear it after use. CSV files go only to the document destination selected by the operator.
- Selecting **Request** explicitly opens an external ClearExams document generator using the selected number.
- Historical contact analysis removes *date/time* limits only. Other active filters (including source files,
  subjects and event types) continue to apply. First-call date means earliest timestamped **voice call**,
  not earliest SMS. A missing/unparseable timestamp is not treated as a first call.
- Network towers and shared contacts provide investigative leads, not conclusive co-location evidence.

## Build and tests

Pull requests into `main` run `gradle testDebugUnitTest`, `gradle assembleDebug`, and `gradle bundleRelease`.
Pushing to `main` runs the same build and publishes the Android APK/AAB as GitHub Actions artifacts.
