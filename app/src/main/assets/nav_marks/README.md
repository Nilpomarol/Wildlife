# Navigation mark artwork

The bottom bar's five marks are supplied by the Wildlife product owner and tinted at
runtime — parchment ink on the dark strip, background ink on the selected cream cell, and
a larger olive camera for Capture. Home, Collection, Capture and Profile were refreshed
with the owner's 25 August 2026 exports; Explore retains the 23 August artwork.

| File             | Destination | Subject                          |
| ---------------- | ----------- | -------------------------------- |
| `home.svg`       | Home        | observation hide in a landscape  |
| `collection.svg` | Collection  | open illustrated field guide     |
| `capture.svg`    | Capture     | camera with a long lens          |
| `explore.svg`    | Explore     | binoculars on a folded map       |
| `profile.svg`    | Profile     | ranger in a campaign hat         |

Each file contains a single filled path from the owner's Inkscape export. The runtime loader
fits that path from its own bounds, so the export's page size, `viewBox`, group transform and
editor metadata do not affect layout. `ui/art/NavMarks.kt` still holds hand-drawn fallbacks
and `NavMark` still prefers this directory, so a destination whose file is missing renders a
drawing rather than a blank.

## Replacing or adding one

Same contract as `field_marks/` and `rank_badges/` — they share one loader
(`SvgAssetPaths`, which reads the raw `d` attribute of the **first** `<path>`):

- **One `<path>` only.** Union every shape before exporting (Inkscape: *Path → Union*, then
  *Path → Object to Path* on strokes or text). Extra paths are silently ignored — see the
  warning below.
- **Inkscape orientation** (`SvgOrigin.INKSCAPE`), already in screen orientation, not a
  potrace export. Group transforms are irrelevant: the fit comes from the path's own bounds.
- **Filled, not stroked.** A `stroke` attribute is dropped; outline art must be converted to
  a filled path.
- **No colour.** Everything is tinted at runtime.
- **Any proportion is fine.** `NavMark` scales each mark by the square root of its aspect
  deviation, so a 2.18:1 camera and a 0.76:1 figure cover the same optical area. Do *not*
  bake padding into the artwork to fake this — padding shrinks the mark instead.

## Warning: the first path wins

The supplied `HomeIcon.svg` contained **two** paths, the first being a leftover copy of the
profile figure from the previous export in the same document. Dropped in unedited it would
have rendered the ranger on the Home tab, silently and with no error. When re-exporting,
check the path count before trusting the file.
