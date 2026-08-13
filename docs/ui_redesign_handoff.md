# Wildlife — UI Redesign Handoff

**Date:** 13 August 2026
**Scope:** Product-facing UI lift toward a bolder, gamified *Field Guide Classic*.
**Status:** In progress, phase-by-phase. Home + Collection landed; Explore, Species Detail and Profile/Home polish remain.
**Authoritative style:** [`style.md`](style.md) (see the new §28) › [`ui_architecture.md`](ui_architecture.md) › product truth in [`Wildlife_prd.md`](Wildlife_prd.md).

---

## 1. Direction decisions

The user asked for a **more gamified, visual, "collectible"** feel — bolder than the original restrained baseline — while keeping the field-guide soul. Agreed constraints:

- **Bolder than the previous docs.** `style.md` §28 ("Adopted revision — a bolder game layer") now documents this so the system stays coherent. Still prohibited: neon, glow, particles, animated gradients, fantasy chrome, arcade fonts, paper-texture skeuomorphism.
- **Rarity and completion % are a labelled visual placeholder** (v1). No fabricated biology, no logic/data changes:
  - Rarity is derived deterministically from a stable species key via `sampleRarityFor(key)` in `SpeciesCard.kt`.
  - Completion meters show a real observed count against a clearly-labelled *provisional* target (`SAMPLE_REGION_TARGET`), never a curated total.
  - Both are to be replaced in place once the catalogue denominator is frozen and a reviewed seasonal rarity snapshot exists (PRD §6.1, §7.1) — with no change to component contracts.
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

## 4. Remaining phases (phase-by-phase, await review each)

1. **Explore** — apply rarity stars + taller cards; restyle the Near-me / Species-guide section switch (currently two full-width `FilterChip`s) into a field-guide tab treatment; keep the truthful provisional/frequency wording.
2. **Species Detail** — richer hero (multi-photo affordance), fuller facts grid, reorder to **observations before About** (matches style §13 and the reference), gamier identity block; keep "only sourced facts" and attribution rules.
3. **Profile / Home consistency** — de-duplicate stats across Home/Collection/Profile (Phase 2 of the original plan), align progression visuals with the new game layer.

---

## 5. Known follow-ups / notes

- **Placeholder replacement:** swap `sampleRarityFor` and `SAMPLE_REGION_TARGET` for real capped rarity + frozen catalogue denominator when available (PRD §6.1/§7.1). The UI contracts are designed to absorb this without visual change.
- **Stray file:** a `window.xml` (UI Automator dump from an unrelated app, `cat.receptari.app.debug`) was found in the repo root and deliberately **excluded** from commits. Delete it.
- **Package/dir mismatch (pre-existing):** sources live under `com/ecotracker/feasibility` but declare `package com.wildlife.feasibility`. Not addressed here.
- Design-doc governance lives in `style.md`; update §28 if the game-layer intensity changes again.
