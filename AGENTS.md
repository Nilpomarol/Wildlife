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
- Wildlife photography is the main visual content. Never ship AI reference imagery or an unlicensed remote image. Preserve source, author, licence code and attribution for every reusable photo.
- Common names lead; scientific names are italic and secondary. Never invent biological facts, rarity, range or conservation data to satisfy a design.
- Rarity, verification and observed state are separate concepts and must use separate semantic indicators.
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
