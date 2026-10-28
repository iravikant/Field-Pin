# FieldPin

Offline-first Android app for inspecting outdoor sites. A field officer maps sites (parks, water
tanks, solar installations), checks in **only when physically inside the boundary**, and records
evidence:
condition, notes and CameraX photos with a stamp (site, lat/lng, GPS accuracy, time) **burned into
the saved JPEG**. Everything works in airplane mode and syncs when the network returns.

> Built for the FieldPin Android take-home. Kotlin · Jetpack Compose · Room · Koin · CameraX · Fused
> Location · Google Maps (maps-compose).

## Demo login

| Email                    | Password    |
|--------------------------|-------------|
| `inspector@fieldpin.app` | `Field@123` |

The session survives an app restart. Logout deletes the local database, photos, documents and sync
state.

## Build and run

Requirements: Android Studio (recent, with the Android 37 SDK platform) or just a JDK 17+ and the
Android SDK.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
```

**Google Maps key.** The app builds without any extra file, but the map only draws tiles with a key:

1. Google Cloud Console → enable **Maps SDK for Android** → *Credentials → API key*.
2. Restrict the key to Android apps: package `com.fieldpin` + your debug/release SHA-1.
3. Copy `local.properties.example` to the git-ignored `local.properties` and set `MAPS_API_KEY=...`.

The key is injected into the manifest at build time (`manifestPlaceholders`); it is never in source
or history. Without it the polygons, pins and all other features still work and the map shows a "key
missing" notice. The same file holds the optional per-build-type base URLs.

`minSdk 26`, `targetSdk 36`, package `com.fieldpin`.

## Trying it without being in Delhi

The 24 seeded sites are in Delhi, so the geofence will (correctly) say "outside" anywhere else. Two
ways to test:

1. **Map your own site** where you stand: *Sites → +*, tap 3+ corners around yourself (or *GPS
   point*), save, then check in. On an emulator, set a location in *Extended controls → Location*
   first.
2. **Debug builds only:** *Account → Developer tools → Teleport me inside a site* simulates a GPS
   position inside any site. It is a clearly labelled override of the location source, off by
   default, and absent from release builds. The same panel has *Simulate another phone editing a
   site* for the conflict demo below.

## Feature checklist

| #     | Requirement                                                                                                                                                                                                      | Status |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| 1     | Login (mock), session survives restart, logout clears local data                                                                                                                                                 | Done   |
| 2     | Sites list: name, category, area, last inspection, status, *Pending sync* badge, search, status filter, 24 seeded sites                                                                                          | Done   |
| 3     | Map (Google Maps): status-coloured polygons **with a 3D pin on every site**, live position + accuracy circle, my-location, satellite and 3D-tilt toggles, tap pin/polygon → bottom sheet with name/area/distance | Done   |
| 4     | Map a new site by tapping corners (satellite view, draggable numbered corners); geodesic area (m², acres) and perimeter                                                                                          | Done   |
| 5     | Geofenced check-in (point-in-polygon, distance shown when outside), condition, notes, CameraX camera with stamp burned into the file                                                                             | Done   |
| 6     | Offline-first, *Sync now*, conflict handling explained below; **map saved on the device on first run**                                                                                                           | Done   |
| Perm  | Asked on first use with a rationale; Deny and "Don't ask again" (→ Settings link) handled; GPS-off handled; no background location                                                                               | Done   |
| Tests | Area, perimeter, point-in-polygon (+ check-in policy, merge, rebase)                                                                                                                                             | Done   |
| CI    | GitHub Actions builds the APK and runs tests on every push                                                                                                                                                       | Done   |

**Bonus items done:** reject self-crossing boundaries · draggable corners · add a corner from GPS
(ignores readings worse than 20 m; a manual cousin of walk mode) · export a site as GeoJSON through
the share sheet · site documents (attach files, preview them in-app) · zoomable photo viewer ·
dark/light/system theme (map style follows it) · automatic sync while the app process is alive (on
reconnect and when changes are queued).

**Deliberately cut (and why):** continuous *walk mode*, WorkManager background sync and the Contacts
picker. The brief says four features done well beat seven half-done, so I spent the time on
correctness of 1-6 (geodesy, geofence, stamping, sync). The offline map is a small saved tile pack
(see *Offline map*).

## App screenshots

<img width="1080" height="2400" alt="Screenshot_2026-10-28-20-51-09-73_63d656c23daf5079fc11e1266c02c638" src="https://github.com/user-attachments/assets/c215da17-0c06-4aab-8aee-cfc3169eba88" />
<img width="1080" height="2400" alt="Screenshot_2026-10-28-20-50-49-32_63d656c23daf5079fc11e1266c02c638" src="https://github.com/user-attachments/assets/687496ac-a3b9-4ba1-b51f-39206470c768" />
<img width="1080" height="2400" alt="Screenshot_2026-10-28-20-47-50-10_63d656c23daf5079fc11e1266c02c638" src="https://github.com/user-attachments/assets/e2704a2f-25ef-4808-a3ab-8389d7465fcc" />

## Offline map (saved on first run)

On the first run, after sign-in and the first sync, a one-time **"Saving your map"** screen appears
while the app downloads a small tile pack to app-private storage (`filesDir/map_tiles`, so Android
does not evict it): a coarse overview of all sites (zoom 10-13) plus detailed tiles hugging each
site (zoom 14-17), about 450 tiles / 6 MB for the seed data. It resumes by itself if the connection
drops, skips tiles already saved, and can be refreshed or deleted from *Account → Offline map*.

- **Basemap switching** (`Basemap.kt`): online → Google's live map (normal or satellite). Offline
  with a saved pack (or no Google key) → Google's base map is switched off (`MapType.NONE`)
  and `CachedTileProvider` draws the saved tiles in a `TileOverlay`: disk first, then network, then
  a scaled-up parent tile so zooming never shows holes. Tiles fetched while online are saved too,
  and are darkened on the fly in dark theme.
- **Why not Google's own tiles?** The Google Maps SDK terms do not allow downloading or storing its
  tiles, and the SDK has no offline API. So the saved map is OpenStreetMap-style tiles from
  `BuildConfig.TILE_URL_TEMPLATE`
  (default: OSM's public server; set `TILE_URL_TEMPLATE` in `local.properties`). That small one-off
  pack with a descriptive User-Agent suits a demo; for production use a provider whose terms allow
  offline storage (self-hosted, MapTiler, Stadia...). The tile credit "© OpenStreetMap contributors"
  is shown whenever those tiles are on screen.
- Tests: `OfflineAreaPlannerTest` (slippy-map tile indices, coverage, ordering, cap).

## Architecture

```
ui/            Compose screens + ViewModels (state in StateFlow, one-off events in Channels)
  components/  design system (cards, pills, buttons) + permission flow
domain/        pure Kotlin: models, GeoMath (area/distance/inside), CheckInPolicy  ← no Android imports, unit tested
data/
  local/       Room (sites, inspections), SharedPreferences session/prefs
  remote/      FieldPinApi interface, in-process MockFieldPinApi, ServerMerge (conflict rules)
  repo/        Site/Inspection/Auth repositories, SyncManager
  location/    FusedLocationProvider wrapper (foreground only)
  camera/      PhotoStamper (burns the stamp into the JPEG)
di/            Koin module
```

```
 Compose screen ──► ViewModel ──► Repository ──► Room (source of truth)
                                      │
                                SyncManager ──► FieldPinApi ──► mock server (JSON file)
```

Choices:

- **MVVM + repositories, Room as the single source of truth.** Screens only observe Room flows; the
  network is never in the read path, which is what makes
  "works in airplane mode" true by construction rather than by special-casing.
- **Domain layer without Android** so the maths that decides money/safety questions (am I inside?
  how big is it?) is JVM-unit-testable.
- **Koin** over Hilt: no annotation processing, so fewer build moving parts (Room's KSP is the only
  processor).
- **Google Maps Compose** (`ui/map`): `FieldMapView` applies a branded dark/light map style,
  `SiteOverlays` draws polygons and **3D pins** (glossy, shaded teardrops rendered once per status
  with Canvas and cached in `PinIcons`),
  `UserLocationOverlay` draws our own dot from the fused-location flow (so the debug "teleport"
  override works), `DraftOverlay` draws draggable numbered corners. The key comes from
  `local.properties`; the trade-off is that a key is needed to see tiles, which the app explains
  with a banner. Tiles are cached by Google Maps for short offline gaps only; there is no offline
  tile pack.
- **Mock server is real enough:** `MockFieldPinApi` persists to a JSON file, adds latency, fails
  with `OfflineException` when the device has no internet, and does conflict resolution server-side
  through the pure `ServerMerge`. Swapping in Retrofit means implementing `FieldPinApi` against
  `BuildConfig.API_BASE_URL` (set per build type in `app/build.gradle.kts`, overridable via
  `local.properties`). No server addresses live in Kotlin.
- **Location** is requested only while a screen that needs it is collected
  (`collectAsStateWithLifecycle` + `WhileSubscribed`): leave the screen or background the app and
  `removeLocationUpdates` is called. `ACCESS_BACKGROUND_LOCATION` is not declared.

## How area, perimeter and "inside" are computed

- **Area** (`GeoMath.areaSqm`): never a flat x/y on degrees. Vertices are converted to *authalic
  latitudes* (the equal-area sphere of the WGS84 ellipsoid, Snyder eq. 3-12)
  and the spherical polygon area is summed from great-circle edges (per-edge polar-triangle formula)
  times the authalic radius². Checked against a Python reference:
  a 100 m × 100 m square in Delhi gives 9,999.95 m²; a 0.001° box at the equator gives 12,309.07 m².
- **Perimeter** (`GeoMath.perimeterM`): sum of **Vincenty inverse** geodesic distances on WGS84
  (haversine only as a non-convergence fallback). Tests assert 1° of longitude at the equator =
  111,319.49 m and 1° of latitude = 110,574.39 m.
- **Inside** (`GeoMath.contains`): ray casting on lat/lng, exact enough at site scale. The check-in
  rule lives in one place (`CheckInPolicy`):
  inside **and** GPS accuracy ≤ 50 m. A fix that is inside but worse than that is reported as "weak
  fix", so a 200 m-wide error circle cannot "check in" to a 30 m tank. When outside, the distance to
  the nearest edge is shown (`distanceToPolygonM`, local equirectangular projection).
  `InspectionRepository.startInspection` **re-evaluates the policy against the stored boundary** —
  the UI button state is never the only gate.
- **Self-crossing check:** pairwise segment intersection on non-adjacent edges; the Save button
  stays disabled and the outline turns red/dashed.

## Site details, documents and previews

The site details screen shows the outline with status/category/size at a glance, key numbers, the
geofenced check-in, status, details, a **Photos** strip (every inspection photo of the site), a
**Documents** list and the inspection history.

- **Attach documents:** *Documents → Add* opens the system file picker (any type, several at once,
  up to 25 MB each). Files are copied into app-private storage (`files/documents/<siteId>/`),
  recorded in the `site_documents` Room table, and wiped on logout. They are **local-only**; they
  are not part of sync.
- **Preview:** tap a photo for a full-screen swipeable viewer (pinch / double-tap to zoom, share).
  Tap a document for an in-app preview:
  images zoom, PDFs render page by page (`PdfRenderer`), text-like files (txt, csv, json, md, xml…)
  show as text. Other types (Word, Excel, CAD…)
  show their details with **Open with…** / **Share** through the app's `FileProvider`. A PDF that is
  protected or damaged falls back the same way.
- The same viewer is used for inspection photos (history sheet and the draft screen, where a photo
  can still be deleted).

## Photos and the stamp

CameraX `ImageCapture` saves a JPEG; `PhotoStamper` then decodes it (down-sampled above ~2800 px),
applies the EXIF rotation, draws the stamp panel (site name, `28.6139° N, 77.2090° E ±6 m`,
`27 Oct 2026, 10:44:12 IST`) with `Canvas`, re-encodes over the file and writes GPS EXIF. The stamp
is therefore part of the pixels, not a preview overlay. Photos live in app-private storage and are
wiped on logout. The mock server stores file names only; a real backend would take the uploads
(listed under next steps).

## Offline sync and conflicts

**What is stored:** every site row carries `version` (the server version it is based on),
`dirtyFields` (the fields edited locally) and `editedAt`. Inspections are append-only rows with a
`synced` flag. A site created offline carries a `created` marker. The *Pending sync* badge and the
sync counter come straight from those columns.

**Sync = push, then pull** (`SyncManager`, guarded by a mutex): push dirty/created sites → push
submitted inspections → pull all sites and rebase them under anything still pending. It runs from
*Sync now*, and automatically (while the app process is alive) when the network comes back or new
changes are queued. If anything fails the local data is untouched and retried later.

**Same site edited on two phones while offline** — the server merges **per field** (`ServerMerge`):

1. Phone A and Phone B both hold version 5. A pushes first: the server accepts it and records
   version 6 plus *which fields changed at version 6*.
2. B pushes with `baseVersion = 5` and only the fields B changed.
    - Fields the server did **not** change since version 5 are accepted. (A changed `status`, B
      changed `name`: both survive.)
    - A field changed on the server since B's base is a **conflict**: the **newer device edit wins**
      (last-writer-wins on `editedAt`). `lastInspectionAt` always keeps the **later date**, because
      an inspection that happened is never "undone".
3. Inspections never conflict: they are new immutable rows keyed by UUID and the push is idempotent.
4. The server returns the merged site plus every conflict. B's app replaces its row with the result
   and lists each conflict (what you set, what the other phone had, who won) under *Account → Sync*,
   so nothing changes silently.

Known limits: LWW trusts device clocks (a phone with a wrong clock can win unfairly — the next step
is server-assigned timestamps or vector clocks); boundaries are not editable after creation, so
there is no geometry merge; there are no deletes yet. An edit made *during* a sync is detected by
comparing `editedAt` and stays pending instead of being overwritten.

## Permissions

Location and camera are requested **only when first needed** (map, check-in, camera), each with an
explanation screen first. States handled: never asked, denied (try again), *Don't ask again* (button
becomes **Open Settings**), approximate-only location (precise is required, treated as not granted),
and location switched off (banner with *Turn on*).
`rememberPermissionController` tells "never asked" from "permanently denied" by remembering that the
system dialog was shown.

## Testing

`./gradlew testDebugUnitTest` — `OfflineAreaPlannerTest`, `GeoMathTest` (area vs ellipsoid values,
winding independence, perimeter, geodesic distances, point-in-polygon incl. a concave notch,
distance to boundary, self-intersection), `CheckInPolicyTest`, `ServerMergeTest` (two-phone
scenarios), `SiteRebaseTest`.

## Data note

Seed sites are in New Delhi. Park locations use real place names at their approximate centres; the
**boundaries are generated from a centre, approximate extents and a vertex count (
`scripts/generate_seed.py`), not surveyed cadastral data**, and the water-tank/solar/substation
sites are fictional locations in plausible areas. Re-run the script to regenerate
`app/src/main/assets/mock_server_seed.json`.

## What I would do next

- Real backend: Retrofit client for `FieldPinApi`, token refresh, photo upload (multipart,
  resumable) before marking an inspection synced.
- WorkManager for sync that survives the app being closed; server timestamps / vector clocks instead
  of device-clock LWW.
- Scale to thousands of sites on the map: query only the viewport's bounding box from Room (R-tree),
  simplify polygons by zoom, cluster centroids at low zoom, rebuild overlays incrementally instead
  of wholesale.
- Mock-location detection (`Location.isMock`), and a trust score from fix age and accuracy history.
- Walk mode with live accuracy, Contacts caretaker picker, uploading site documents with the sync
  (they are local-only today).
- Instrumented/Compose UI tests and a Room migration test (v1→v2 adds `site_documents`); token
  storage in the Android Keystore.

## AI Use

AI tools were used to assist with the Python-based area calculator logic, improve the application
UI, and refine UI layouts and styling. AI-generated suggestions were reviewed and adapted to the
project requirements. I am responsible for testing and validating the final implementation and
understanding the code included in the submission.

## Third-party libraries

Jetpack (Compose, Navigation, Lifecycle, Room, CameraX), Koin, kotlinx.serialization/coroutines,
Google Play Services Location (Fused Location Provider), Google Maps SDK for Android +
`maps-compose` (map, polygons, markers). The area formula follows Snyder, *Map Projections — A
Working Manual*; Vincenty's inverse formula is the standard published algorithm.
