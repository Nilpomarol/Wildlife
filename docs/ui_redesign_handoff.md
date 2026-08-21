# Wildlife — UI Redesign Handoff

**Date:** 13 August 2026
**Scope:** Product-facing UI lift toward a bolder, gamified *Field Guide Classic*.
**Status:** Historical implementation handoff. Home + Collection landed; the active continuation is the regional roadmap dated 21 August 2026.
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
- **New display font: Fraunces** (OFL variable serif) for large titles, species identity and collection/level headers. Lora remains the secondary serif; sans stays for body/UI.
  - Files: `app/src/main/res/font/fraunces_variable.ttf`, `fraunces_italic_variable.ttf`.
  - License: `docs/licenses/Fraunces-OFL.txt`. Provenance: Google Fonts `ofl/fraunces` (SIL OFL 1.1).
  - Wiring: `Type.kt` — `displayLarge`/`headlineLarge`/`headlineMedium` → Fraunces; `DisplayFontFamily` exposed. Screen titles (top bar `headlineLarge`) now render in Fraunces.
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
- **CollectorHeader**: region pill ("Catalonia ▾", visual selector), collector **rank** chip, **Collected / Confirmed / XP** stat HUD in Fraunces numerals, **completion meter** (%) with the provisional/sample disclosure.
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
- **No emulator/AVD or device was available** during this work, so verification was via full `assembleDebug` (green). There are no screenshot/render tests configured; if automated previews are wanted, wire up Roborazzi. Otherwise run on a device/Android Studio to eyeball.
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 4. Superseded continuation

Further UI work follows [`Wildlife_roadmap.md`](Wildlife_roadmap.md): observation-management separation first, then shared regional components and vertical slices. Do not continue the old screen-by-screen rarity placeholder rollout.

---

## 5. Known follow-ups / notes

- **Placeholder replacement:** replace `sampleRarityFor` and `SAMPLE_REGION_TARGET` with real regional catalogue data. `LEGENDARY` moves out of encounter rarity and becomes regional prestige; see `style.md` §4/§28 and `regional_catalogues.md`.
- **Stray file:** a `window.xml` (UI Automator dump from an unrelated app, `cat.receptari.app.debug`) was found in the repo root and deliberately **excluded** from commits. Delete it.
- **Package/dir mismatch (pre-existing):** sources live under `com/ecotracker/feasibility` but declare `package com.wildlife.feasibility`. Not addressed here.
- Design-doc governance lives in `style.md`; update §28 if the game-layer intensity changes again.
