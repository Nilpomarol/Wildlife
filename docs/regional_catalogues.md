# Wildlife — Regional Catalogue Contract

**Status:** Adopted product and data contract
**Date:** 21 August 2026
**Authority:** [`Wildlife_prd.md`](Wildlife_prd.md) remains the product authority. This document defines the regional catalogue system and its authoring inputs.

## 1. Scope decision

Wildlife is moving from one Catalonia-only catalogue to a versioned system of **24 world regions**. The runtime and authoring formats must support adding, splitting or retiring regions without a bespoke application migration.

The region names and country/area groupings below come from the owner-supplied workbook `DOC-20260817-WA0009.xlsm`, sheet `Regions`, cells `A1:D25`. The workbook's `Animals` sheet is explicitly **not** an approved catalogue, Essentials list or Icons list and must not be imported.

The workbook is the product input for the regional grouping, not a runtime asset. Runtime identifiers, ISO country membership and polygons are produced by the catalogue toolchain and reviewed in version control.

## 2. Adopted regions

| ID | Stable key | Region | Country / area grouping supplied by the owner |
|---:|---|---|---|
| 1 | `mediterranean_europe` | Mediterranean Europe | Spain, Portugal, Italy, Greece, Croatia, Albania, Montenegro, Malta, Cyprus |
| 2 | `temperate_europe` | Temperate Europe | France, Germany, Belgium, Netherlands, United Kingdom, Ireland, Switzerland, Austria, Czechia, Poland and related countries |
| 3 | `boreal_europe` | Boreal Europe | Norway, Sweden, Finland, Iceland |
| 4 | `eastern_europe_steppe` | Eastern Europe / Steppe | Romania, Bulgaria, Moldova, Ukraine, Belarus |
| 5 | `siberia_boreal_asia` | Siberia and Boreal Asia | Russia, Mongolia |
| 6 | `maghreb_sahara` | Maghreb and Sahara | Morocco, Algeria, Tunisia, Libya, Egypt, Mauritania |
| 7 | `sahel` | Sahel | Senegal, Mali, Burkina Faso, Niger, Chad, Sudan and related areas |
| 8 | `west_central_tropical_africa` | West / Central Tropical Africa | Guinea to Nigeria, Cameroon, Gabon, Congo, Democratic Republic of the Congo and related areas |
| 9 | `east_africa` | East Africa | Ethiopia, Kenya, Uganda, Tanzania, Rwanda, Burundi, South Sudan |
| 10 | `southern_africa` | Southern Africa | Angola, Zambia, Zimbabwe, Botswana, Namibia, South Africa, Mozambique, Malawi |
| 11 | `madagascar_west_indian_ocean` | Madagascar and Western Indian Ocean | Madagascar, Comoros, Mauritius, Seychelles |
| 12 | `middle_east_arabia` | Middle East and Arabia | Turkey, Syria, Iraq, Israel, Jordan, Saudi Arabia, Oman, Yemen, United Arab Emirates and related areas |
| 13 | `central_asia` | Central Asia | Iran, Afghanistan, Kazakhstan, Uzbekistan, Turkmenistan, Kyrgyzstan, Tajikistan |
| 14 | `indian_subcontinent` | Indian Subcontinent | India, Pakistan, Nepal, Bhutan, Bangladesh, Sri Lanka |
| 15 | `temperate_east_asia` | Temperate East Asia | China, North Korea, South Korea, Japan |
| 16 | `mainland_southeast_asia` | Mainland Southeast Asia | Myanmar, Thailand, Laos, Cambodia, Vietnam, Malaysia, Brunei, Singapore |
| 17 | `insular_southeast_asia` | Insular Southeast Asia | Indonesia, Philippines, Timor-Leste |
| 18 | `australasia` | Australasia | Australia, Papua New Guinea |
| 19 | `new_zealand_pacific` | New Zealand and Pacific | New Zealand and Pacific island states |
| 20 | `temperate_boreal_north_america` | Temperate / Boreal North America | Canada, United States |
| 21 | `mesoamerica` | Mesoamerica | Mexico, Guatemala, Belize, Honduras, El Salvador, Nicaragua, Costa Rica, Panama |
| 22 | `caribbean` | Caribbean | Cuba, Jamaica, Haiti, Dominican Republic, Bahamas and the Antilles |
| 23 | `tropical_north_south_america_amazon` | Tropical Northern South America / Amazon | Brazil, Colombia, Venezuela, Ecuador, Peru, Guyana, Suriname |
| 24 | `andes_southern_south_america` | Andes and Southern South America | Bolivia, Argentina, Chile, Paraguay, Uruguay |

The English names are working localisation copy. Stable keys never change when display copy is translated or refined.

## 3. Boundary completion required

The supplied groupings use phrases such as “related countries”, omit some countries and territories, and do not define offshore coverage. They therefore cannot be used directly for deterministic observation assignment.

Before the 24-region catalogue is frozen, the authoring source must contain:

- every supported sovereign state and relevant territory exactly once;
- explicit handling for transcontinental countries;
- local, versioned land polygons;
- explicit marine polygons or an explicit rule that offshore observations remain unassigned;
- no runtime use of vague labels such as “related areas”;
- a documented policy for Antarctica and other uncovered locations.

### Approved territory index — 21 August 2026

The owner approved the completed Natural Earth Admin 0 Map Units v5.1.1 review worksheet.
`country_territory_assignments.yaml` therefore contains both a 226-entry ISO review index and
298 source-map-unit assignments, including dependent territories and source geometry pieces that
do not have an ISO code. The 11 non-progressing entries are deliberately represented as either
`marine_worldwide` or `unsupported_for_regional_progression`; they are not assigned to one of
the 24 land regions. This establishes the region decision, but it does not by itself substitute
the required local, versioned geometry for runtime point-in-polygon assignment.

The application never assigns a region using a nearest-country guess. If a public obscured coordinate or boundary case is not sufficient for a safe assignment, the observation is labelled `region_uncertain` and does not unlock regional completion or XP.

### Approved marine and uncovered-location policy — 21 August 2026

Marine observations are retained rather than discarded. Wildlife first assigns a point to land
through its local versioned polygons. A non-land point may count toward a land region only when
it falls inside exactly one explicitly reviewed offshore buffer for that region. These buffers
are versioned local polygon assets with recorded source, licence and version; they are never
derived from the user's presumed departure country or nearest coast. An overlapping/ambiguous
buffer result is `region_uncertain` and earns no regional progress.

An unmatched marine point uses the `marine_worldwide` context. It is not a twenty-fifth land
region and has no regional completion, achievement or XP contract until its own curated marine
catalogue is approved. It remains visible in personal history. Antarctica and other uncovered
land likewise remain in personal history but are unsupported for regional progression in v1.

For v1's single-country transcontinental policy, Turkey belongs to Middle East and Arabia,
Russia to Siberia and Boreal Asia, Egypt to Maghreb and Sahara, and Kazakhstan to Central Asia.
The policy is versioned so a future reviewed geographic split can preserve historical assignment
and ledger context.

## 4. Catalogue data model

Global taxon identity and regional membership are separate:

```text
Taxon
  taxon_id, rank, scientific names, localised common names, taxonomy, media provenance

Region
  region_key, localised names, polygon version, display order

CatalogueVersion
  region_key, catalogue_version, season/year, frozen_at, content rules version

RegionalTaxon
  region_key, catalogue_version, taxon_id, encounter_rarity,
  prestige, seasonality, sort order, inclusion/override provenance

RegionalAchievement
  region_key, catalogue_version, achievement_key, type, ordered taxon IDs

ObservationRegion
  observation_uuid, region_key, boundary_version, assignment source, confidence
```

Taxon details and reusable media are deduplicated globally. A species may belong to several regional catalogues, but one observation unlocks only the region containing that observation.

## 5. Catalogue content policy

Catalogues focus on wildlife a normal user can intentionally encounter and photograph:

- mammals, birds, reptiles and amphibians;
- conspicuous, photographable fish selected through an allowlist;
- butterflies, odonates and a restrained set of distinctive invertebrates;
- occasional difficult species when they provide genuine regional collection value.

Routine vagrants, accidental records, taxa normally impossible to identify photographically and exhaustive invertebrate coverage are excluded. Inclusion is curated; raw iNaturalist reporting frequency never becomes the catalogue automatically.

Each catalogue is frozen and versioned. Catalogue updates may change projected completion, but never remove historical XP or an already-earned achievement.

## 6. Encounter rarity and regional prestige

Wildlife uses two independent dimensions:

1. **Encounter rarity:** `common`, `uncommon`, `rare`, `very_rare`. This estimates how difficult the species is to encounter and photograph in that region, using a versioned regional evidence snapshot plus review.
2. **Regional prestige:** `standard` or `legendary`. Legendary is a manually curated game designation for the animals that define the region. It is not a claim of scarcity, threat or verification quality.

This permits truthful combinations such as:

```text
African elephant — Common · Regional Legend
Common warthog — Common · Standard
```

### Broad draft catalogue entries

Broad pilot catalogues may contain an `unknown` encounter-rarity state while editorial
review is pending. This means only that Wildlife has not curated an encounter tier; it must
not be displayed as Common or used for a rarity reward. Regional Essentials and Icons retain
their reviewed rarity and prestige fields. A later frozen catalogue version replaces the
draft state without changing historical progression.

Regional Icons and Legendary prestige are deliberately independent authoring decisions. An Icon may carry `standard` or `legendary` prestige, and any regional catalogue species may be marked `legendary` without becoming an Icon. Icons remain the fixed five-species achievement checklist; Legendary remains a regional collectible-prestige designation. Legendary prestige is regional: the same taxon may be Legendary in one catalogue and Standard in another.

## 7. Regional achievements

Every frozen regional catalogue defines:

- **Regional Essentials:** 10 manually curated, reasonably attainable species that introduce the region.
- **Regional Icons:** 5 manually curated, high-prestige species that express the region's wildlife identity and may be substantially harder to complete.

The two sets are curated independently from raw frequency. A taxon should not appear in both sets for the same catalogue unless a reviewed exception explains why.

Achievement identity includes region, catalogue version and type. Once awarded, an achievement remains in history even if a later catalogue version changes its checklist.

## 8. Active catalogue and browsing

- Location is sampled only after an explicit user action or permission grant; it is never monitored continuously.
- A confident local polygon match may suggest an active catalogue.
- The app remembers the user's selected catalogue and never interrupts browsing with an automatic switch.
- The user can browse every installed catalogue and manually choose the active one.
- The currently relevant catalogue metadata, achievement definitions, rarity and thumbnails should be local. Other regional media may be delivered as optional local packs.

## 9. Authoring contract

The future developer toolchain uses reviewable source files rather than Android source-code edits:

```text
catalogues/
  regions.yaml
  boundaries/
  taxa.yaml
  regions/<region_key>/catalogue.yaml
  regions/<region_key>/overrides.yaml
  achievements.yaml
  media_manifest.yaml
```

The generator produces a deterministic SQLite catalogue, boundary assets, localisation checks, media/provenance manifests and a human-readable diff. Validation fails when:

- a region key, catalogue version or taxon ID is invalid;
- a country/territory has zero or multiple region assignments;
- an Essentials or Icons list has the wrong size;
- a listed achievement taxon is not in the regional catalogue;
- an unexplained taxon appears in both regional checklists;
- rarity, prestige or override provenance is missing;
- reusable media lacks creator, source and compatible licence metadata.

The generator deliberately does **not** require an Icon to be Legendary, nor a Legendary species to appear in the Icons list. Editors set `prestige: legendary` directly on the relevant `RegionalTaxon` entry and record its curation reason in `inclusion_provenance`.

## 10. Pilot strategy

The engine targets all 24 regions, but the pipeline is proven with three contrasting pilots before bulk curation:

1. Mediterranean Europe — migration path from the current Catalonia prototype.
2. East Africa — validates common-but-Legendary species and large iconic fauna.
3. Caribbean — validates island/territory boundaries and media packaging with a more manageable
   first curation scope than Insular Southeast Asia.

Pilot choice may change without changing the architecture. Scaling the remaining catalogues begins only after deterministic generation, assignment tests and catalogue diffs are trustworthy.

### Pilot content status — 21 August 2026

Mediterranean Europe, East Africa and the Caribbean each contain 15 owner-directed,
photographable species: ten distinct Regional Essentials and five distinct Regional Icons.
Their encounter rarity and Legendary values are curated game metadata, not conservation
claims. All three v1 catalogues are frozen with licence-verified reusable media, source and
creator provenance, and pass deterministic release validation.
