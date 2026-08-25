# Wildlife — UI Redesign Handoff

**Date:** 13 August 2026; pipeline pause reopened after the 24 August 2026 post-cutover audit
**Scope:** Product-facing UI lift toward a bolder, gamified *Field Guide Classic*.
**Status:** **READY TO RESUME.** The post-cutover remediation gate in `species_content_pipeline_plan.md` closed on 24 August 2026.
**Authoritative style:** [`style.md`](style.md) (see the new §28) › [`ui_architecture.md`](ui_architecture.md) › product truth in [`Wildlife_prd.md`](Wildlife_prd.md).

---

## 1. Direction decisions

The user asked for a **more gamified, visual, "collectible"** feel — bolder than the original restrained baseline — while keeping the field-guide soul. Agreed constraints:

- **Bolder than the previous docs.** `style.md` §28 ("Adopted revision — a bolder game layer") now documents this so the system stays coherent. Still prohibited: neon, glow, particles, animated gradients, fantasy chrome, arcade fonts, paper-texture skeuomorphism.
- **Rarity and completion % are a labelled visual placeholder**. No fabricated biology, no logic/data changes:
  - Rarity is derived deterministically from a stable species key via `sampleRarityFor(key)` in `SpeciesCard.kt`.
  - Completion meters show a real observed count against a clearly-labelled *provisional* target (`SAMPLE_REGION_TARGET`), never a curated total.
  - Both are to be replaced when the regional catalogue model lands. `encounterRarity` and regional `prestige` become independent fields; this supersedes the original assumption that no component-contract change was needed.
- **UI-only.** No business logic was changed. The one exception, explicitly approved: a single read-only derived field (`ShellUiState.latestDiscovery`) computed from data the shell already loads, so Home can show a real photo.

---

## 2. Implemented so far

### Theme
- **Display font: Eczar** (OFL wedge serif) for large titles, species identity and collection/level headers. Barlow is the sans for body/UI. Superseded Newsreader, which superseded Fraunces, which superseded Lora.
  - Files: `app/src/main/res/font/eczar_{regular,medium,semibold,bold}.ttf` — one static instance per weight, subset to Latin (~36KB each, down from 264KB).
  - License: `docs/licenses/Eczar-OFL.txt`. Provenance: Google Fonts `ofl/eczar` (SIL OFL 1.1).
  - Wiring: `Type.kt` — `displayLarge`/`headlineLarge`/`headlineMedium`/`titleLarge` → Eczar; `DisplayFontFamily` exposed.
- **Rarity tokens** added to `WildlifeColors` (`Color.kt`, provided in `WildlifeTheme.kt`): common/uncommon/rare/very-rare/legendary per style §4.

### Home (`ui/screens/shell/HomeScreen.kt`)
Rebuilt from a button-menu into an **image-led dashboard**:
- Latest-discovery **photo hero** (real most-recent observed species with a photo; monogram fallback), tap → species detail.
- Tappable "Field record" summary, actionable observation queue, My-map entry.
- Removed redundancy: the top "Record" button (Capture is the centre tab) and the Collection/Explore button row (both are bottom-nav destinations).
- Unlinked and empty-collection states redesigned.
- Data: `ShellViewModel` exposes read-only `HomeHighlight latestDiscovery`. `WildlifeShellActivity` wires `onOpenSpecies`.

### Species card (`ui/components/SpeciesCard.kt`) — shared by Collection + Explore
- **Rarity star** (top-left), tier-coloured, with accessible label folded into a merged card content description.
- **Tier border** (1.5dp coloured) for Rare and above.
- **Crossfade** photo↔silhouette; Coil `crossfade(true)` for a reveal feel.
- Stronger bottom scrim; species name promoted to `titleMedium` semibold.
- New optional field `SpeciesCardModel.rarity` (defaults null → backward compatible).

### Collection (`ui/screens/collection/CollectionScreen.kt`) — ground-up
- **CollectorHeader**: region pill ("Catalonia ▾", visual selector), collector **rank** chip, **Collected / Confirmed / XP** stat HUD in display-serif numerals, **completion meter** (%) with the provisional/sample disclosure.
- **Taller cards** (aspect 0.94 → 0.72) to match reference proportions.
- Rarity wired via `sampleRarityFor(key)`.

---

## 3. Build & verify

- **JDK 21 required** (Gradle/AGP need 11+). The system default is Java 8. Use the Android Studio JBR:
  ```bash
  export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
  ./gradlew assembleDebug
  ```
- Compile-only check: `./gradlew compileDebugKotlin`.
- On 24 August 2026, the current remediation build passed the debug build and 189 unit tests,
  installed on one connected device, and cold-started without a crash. After moving projections
  and startup maintenance off the UI thread, three cold starts measured 1,238 ms, 1,039 ms and
  970 ms versus the previous 6,003 ms baseline. A rapid Collection/Explore/Profile/Home navigation
  and scrolling pass produced no crash or ANR; modern frame metrics reported 8.68% janky frames,
  so this remains a smoke check rather than the representative-device performance gate.
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 4. Continuation status

All five remediation items are complete: shared bounded content reads, demand/state separation, asynchronous projections, a transactional due-time-driven media queue, and schema-v3 sequence-based publication validation with a production-path 25-region gate. Broad UI work may resume.

Continue from the shared UI consolidation path. Pilot content promotion, coverage expansion and the representative-device release matrix remain separate work; do not revive the old screen-by-screen rarity placeholder rollout.

---

## 5. Known follow-ups / notes

- **Placeholder replacement:** replace `sampleRarityFor` and `SAMPLE_REGION_TARGET` with real regional catalogue data. `LEGENDARY` moves out of encounter rarity and becomes regional prestige; see `style.md` §4/§28 and `regional_catalogues.md`.
- **Stray file:** a `window.xml` (UI Automator dump from an unrelated app, `cat.receptari.app.debug`) was found in the repo root and deliberately **excluded** from commits. Delete it.
- **Package/dir mismatch (pre-existing):** sources live under `com/ecotracker/feasibility` but declare `package com.wildlife.feasibility`. Not addressed here.
- Design-doc governance lives in `style.md`; update §28 if the game-layer intensity changes again.
