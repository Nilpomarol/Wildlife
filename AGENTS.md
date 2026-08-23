# Wildlife repository instructions

These instructions apply to every agent and contributor working in this repository.

## Product constraints

- Wildlife has no iNaturalist OAuth permission. Keep the integration read-only and use the documented handoff to the official iNaturalist app.
- iNaturalist is the biological source of truth. Wildlife owns only its cache, collection projection, confirmation state and game layer.
- Do not introduce undocumented iNaturalist write paths, credentials or tokens.

## UI authority

Before planning or implementing UI, read completely:

1. `docs/style.md` — visual and interaction authority.
2. `docs/ui_architecture.md` — implementation and migration authority.
3. The relevant product behavior in `docs/Wildlife_prd.md`.

If a concept screenshot conflicts with these documents, the documents win. The screenshots in `docs/assets/` communicate mood, density and visual character only; they are not layout, content or data specifications.

## UI implementation rules

- Use Jetpack Compose and Material 3 for new product-facing screens.
- Apply the final **Field Guide Classic** style whenever a product screen is newly built or materially changed. Do not add more temporary programmatic-View UI to product screens.
- Existing validation and account-management screens may remain utilitarian until touched. When touched, use standard themed Material components rather than one-off decoration.
- Reuse theme tokens and shared components. Do not scatter raw colors, typography, corner radii or spacing values through feature composables.
- Before introducing any visual value, inspect `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `Spacing.kt` and `WildlifeTheme.kt` for an existing semantic token. Use the existing token whenever its meaning fits; do not create a near-duplicate for a single screen.
- Add a shared style token when a semantic value or visual role recurs across components, is required by the adopted style contract, or is expected to recur in the roadmap. Name tokens by product meaning or design role, never by a screen name or one temporary layout.
- Colours, typography styles, standard spacing, corner radii, borders, elevation and motion durations must come from the shared theme/token layer. A one-off structural measurement such as a hero height or map viewport may remain local when it is genuinely component-specific and is not a disguised design token.
- When a second use of a local visual constant appears, move it to the appropriate theme/token file as part of that change and update both call sites. Do not maintain parallel aliases with the same value and meaning.
- Extend semantic token models centrally and update representative previews when introducing a new visual state. Do not repurpose an existing token whose semantic meaning differs merely because its current numeric value looks suitable.
- Before creating a UI component or layout pattern, inspect `ui/components` and current call sites for an existing shared implementation. Prefer extending an existing component with typed parameters, variants or slots over creating a visually similar screen-specific component.
- A pattern used by two or more screens, or expected by the documented roadmap to recur, belongs in the shared component layer. Species grids/cards, region selection/progress, observation rows/tiles, achievement presentation, section headers and loading/empty/error treatments must not be reimplemented independently per screen.
- Keep screen-private composables only for genuinely one-off composition that carries no reusable product behavior or visual identity. If a second use appears, extract the common contract as part of that change and remove the duplicate implementation.
- Shared components own presentation and accessibility contracts; screen state/projections own product data and decisions. Do not put repository access, taxonomy rules, rarity calculation or navigation policy inside reusable visual components.
- Wildlife photography is the main visual content. Never ship AI reference imagery or an unlicensed remote image. Preserve source, author, licence code and attribution for every reusable photo.
- Common names lead; scientific names are italic and secondary. Never invent biological facts, rarity, range or conservation data to satisfy a design.
- Encounter rarity, regional standing (Essential / Icon), conservation, verification and observed state are separate concepts and must use separate semantic indicators.
- Support system-bar insets, 48dp tap targets, font scaling, screen readers and non-colour state labels from the first implementation.
- New product UI must include Compose previews for reusable visual components and state variants when practical.

## Progressive migration

- Migrate by vertical slice, not by a full-app rewrite.
- Build the theme and core components first, then convert Collection, Catalogue and Species Detail as they are implemented.
- Keep data, repositories and projections independent of Activities and composables.
- Do not maintain two permanent implementations of one screen. Remove the old screen once its Compose replacement is verified.

## Verification

- Run Android unit tests and build the debug APK after UI changes.
- Validate at least observed, unobserved, loading, empty, offline/error and large-font states relevant to the changed screen.
- Update `docs/ui_architecture.md` when a reusable UI contract or navigation structure changes.
