# Wildlife Progression Rules — Regional runtime v0.2

**Status:** Implemented for internal development; XP values remain deliberately experimental
**Date:** 21 August 2026
**Authority:** `Wildlife_prd.md` remains authoritative. This file records the current runtime configuration and the decisions that remain editable before release.

**Catalan review copy:** [`progression_rules_ca.md`](progression_rules_ca.md)

**Implementation:** `ProgressionRules.kt` implements `progression-0.2-regional-experimental`. It adds regional events without rewriting existing ledger entries. The regional catalogue, rarity, prestige and achievement contracts are defined in [`regional_catalogues.md`](regional_catalogues.md).

## 1. How to revise this proposal

All tunable decisions are collected in the tables in Sections 3–6. A reviewer should change the values in those tables and record the decision in Section 10. Narrative sections define safety and data behavior and should change only when the underlying product contract changes.

Rules use stable keys such as `confirmed_observation` and `field_ranger`; UI copy may be translated or renamed without changing stored events. Implementation must keep the rules version separate from the XP ledger so thresholds can change without rewriting earned XP.

## 2. Non-negotiable rules

- Only the verified immutable iNaturalist user ID can earn progression.
- Wildlife remains read-only; no progression mechanic may require an undocumented iNaturalist write path.
- XP events are append-only and idempotent. A retry must never award the same event twice.
- XP is never subtracted after identification or taxonomy changes.
- Collection projections may change, but ledgered XP remains historical fact.
- Levels and rewards are cosmetic. They never change observation visibility, scientific status, matching confidence or access to biological information.
- Rarity, verification and observed state remain separate concepts.
- Encounter rarity, regional Legendary prestige, conservation status and verification are four separate concepts.
- A species may unlock only the regional catalogue containing the observation. A taxon seen elsewhere never counts toward that region.
- An uncertain regional assignment awards no regional XP until it can be resolved safely.
- Historical observations imported during initial account sync unlock the collection but do not earn retroactive XP in this placeholder version.
- An ambiguous handoff earns nothing until the user explicitly confirms the matching public observation.

## 3. XP event configuration

These keys and values are the editable source for the current internal implementation.

| Event key | Trigger | XP | Enabled | Notes |
|---|---|---:|---|---|
| `confirmed_observation` | A matched public observation is explicitly confirmed in Wildlife | 10 | Yes | Matches the current ledger |
| `first_species` | The user's first confirmed species-level collection taxon globally | 500 | Yes | Existing stable event; remains global and keeps its current value |
| `research_grade` | A previously known public observation first reaches Research Grade | 50 | Yes | Enabled after durable quality-transition detection and idempotency were implemented in lifecycle schema v5 |
| `regional_discovery` | First confirmed unlock of a taxon in the region containing the observation | 100 | Yes | Keyed by region, frozen catalogue version and taxon |
| `regional_rarity_bonus` | Additive encounter-rarity bonus on the first regional discovery | 0–300 | Yes | Never a multiplier; fixed by the catalogue version at award time |
| `regional_legend` | First regional discovery of a manually curated Legendary species | 1,000 | Yes | Prestige reward; independent from encounter rarity |
| `regional_essentials_complete` | Complete the region's 10-species Essentials checklist | 1,500 | Yes | One award per frozen checklist version |
| `regional_icons_complete` | Complete the region's 5-species Icons checklist | 3,000 | Yes | One award per frozen checklist version |
| `identification_given` | A qualifying identification given to another iNaturalist user | 25 | No | v1.2; requires validated source fields, self-exclusion and daily cap |
| `anomaly_confirmed` | A reviewed out-of-range sighting passes the required delay | 250 | No | Requires a versioned range signal; never infer it from Research Grade alone |

### 3.1 Repeat-observation rule

The current app awards `confirmed_observation` once per confirmed public observation. For the placeholder progression release, use the following diminishing-return schedule per species-level collection taxon and ISO week:

| Confirmed observation of the same species in one ISO week | Base observation XP |
|---:|---:|
| 1st | 10 |
| 2nd | 5 |
| 3rd | 5 |
| 4th and later | 0 |

`first_species` and `research_grade` are independent of this repeat schedule. An unidentified or genus-level observation may earn base observation XP, but it earns `first_species` only if a later sync supplies a species-level collection taxon and no corresponding first-species event already exists.

### 3.2 Encounter rarity configuration

Encounter rarity bonuses are enabled for the bundled pilot catalogues. They are additive and apply only when a regional catalogue entry is first unlocked.

| Encounter rarity | First-regional-discovery bonus | Enabled |
|---|---:|---|
| Common | +0 | Yes |
| Uncommon | +50 | Yes |
| Rare | +150 | Yes |
| Very Rare | +300 | Yes |

Raw observation frequency must not be presented as biological rarity. The generator should prefer distinct observation days and spatial coverage over raw totals, exclude accidental/vagrant records from the normal catalogue, and allow reviewed overrides with reasons. Bonuses are frozen with the catalogue version used by the award.

### 3.3 Regional Legendary prestige

`legendary` is a manually curated regional prestige flag, not a fifth encounter-rarity tier. It allows a species to be both easy to encounter and exceptionally valuable to the collection fantasy. For example, an African elephant may truthfully be presented as **Common · Regional Legend** while a common warthog remains **Common · Standard**.

Regional Icons and Legendary prestige are independent: an Icon may be Standard, and any catalogue species may be Legendary. The `regional_legend` reward is awarded once per region, catalogue version and taxon. A species may be Legendary in more than one region, but an observation counts only in the region where it was made.

Illustrative first-discovery totals, before Research Grade:

| Example | Base observation | Global first species | Regional discovery | Rarity | Legendary | Total |
|---|---:|---:|---:|---:|---:|---:|
| Common standard species, first ever sighting | 10 | 500 | 100 | 0 | 0 | 610 |
| Common Regional Legend, first ever sighting | 10 | 500 | 100 | 0 | 1,000 | 1,610 |
| Common Regional Legend already seen in another region | 10 | 0 | 100 | 0 | 1,000 | 1,110 |
| Very Rare standard species, first ever sighting | 10 | 500 | 100 | 300 | 0 | 910 |

These values express game significance without falsely labelling an iconic animal as biologically rare. They remain subject to account simulation before closed beta.

## 4. Level configuration

Level is a projection of lifetime ledgered XP: the user's level is the highest threshold less than or equal to total XP.

| Level key | Display name | Lifetime XP threshold | Approximate first-species milestones | Cosmetic reward |
|---|---|---:|---:|---|
| `tourist` | Tourist | 0 | 0 | Default profile title |
| `explorer` | Explorer | 500 | 1 | Selectable “Explorer” title |
| `naturalist` | Naturalist | 2,500 | 5 | Selectable “Naturalist” title |
| `tracker` | Tracker | 7,500 | 15 | Selectable “Tracker” title |
| `field_ranger` | Field Ranger | 20,000 | 40 | Selectable “Field Ranger” title |
| `master_ranger` | Master Ranger | 50,000 | 99 | Selectable “Master Ranger” title |
| `legendary_ranger` | Legendary Ranger | 100,000 | 197 | Selectable “Legendary Ranger” title |

The milestone column is explanatory only. Levels are calculated from XP, not species count.

### 4.1 Progress calculation

For every level except the last:

```text
progress = (total XP - current threshold) / (next threshold - current threshold)
```

Clamp progress to `0…1`. At the highest level, show the lifetime total without a fabricated next target.

### 4.2 Experimental pacing

Illustrative runtime events, before any later Research Grade reward:

| Scenario | XP awarded | Resulting level from a new account |
|---|---:|---|
| First Common regional species | 610 | Explorer |
| First Very Rare regional species | 910 | Explorer |
| First Common Regional Legend | 1,610 | Explorer |
| Complete Essentials after their final first-regional discovery | +1,500 | Depends on prior discoveries |
| Complete Icons after their final first-regional discovery | +3,000 | Depends on prior discoveries |

These examples no longer predict runtime pacing because regional discoveries, rarity, Legendary prestige and versioned checklist completion are active. Before any release, simulate realistic regional field histories and revise thresholds if users progress too slowly or too quickly. Existing ledger events remain unchanged when values or thresholds are revised.

### 4.3 Preliminary pilot-pack simulation — 22 August 2026

The first deterministic simulation reads the current three generated pilot catalogue sources. It
assumes one explicitly confirmed observation for each distinct regional taxon, no Research Grade
transition, one global first-species award, all regional first-discovery/rarity/Legendary awards,
and both checklist completion awards. It does not treat the catalogue order as a likely field
history.

| Scenario | XP | Projected level |
|---|---:|---|
| 10 Common regional first discoveries | 1,600 | Explorer |
| 50 Common regional first discoveries | 6,000 | Naturalist |
| 200 Common regional first discoveries | 22,500 | Field Ranger |
| Complete Mediterranean Europe (800 taxa) | 129,050 | Legendary Ranger |
| Complete East Africa (931 taxa) | 148,960 | Legendary Ranger |
| Complete Caribbean (813 taxa) | 131,830 | Legendary Ranger |

The complete-pack totals include the current generated rarity and Legendary distributions and the
10-Essentials/5-Icons awards. These results are a useful boundary check, not a beta freeze:
the pilot rarity/prestige fields remain editorially provisional, and realistic user histories must
still be sampled before the product owner approves a release rules version.

## 5. Rewards and unlocks

The runtime grants profile titles only. A user may display any title at or below the highest level they have reached.

| Reward type | Runtime rule |
|---|---|
| Profile title | Enabled; one title per reached level |
| Themes or colour palettes | Disabled |
| Special catalogue access | Prohibited; biological information is never level-gated |
| XP boosters or multipliers | Prohibited |
| Competitive advantage | Prohibited |
| Physical or monetary reward | Out of scope |

If thresholds change after beta begins, an account must not lose an already reached title. Store or derive `highest_level_achieved` during the migration before raising any threshold.

## 6. Badge and streak configuration

Generic badges and streaks remain disabled. Regional Essentials and Regional Icons are active core collection achievements and are versioned separately from the generic badge candidates below.

| Candidate key | Proposed requirement | Enabled | Blocking definition |
|---|---|---|---|
| `first_field_note` | 1 confirmed public observation | No | Badge visual and copy review |
| `ten_species` | 10 distinct confirmed species | No | Decide whether historical collection entries count |
| `fifty_species` | 50 distinct confirmed species | No | Decide whether historical collection entries count |
| `research_contributor` | 10 observations reach Research Grade | No | Research Grade transition sync |
| `night_owl` | 5 qualifying nocturnal observations | No | Local solar-time definition; clock time alone is insufficient |
| `biome_master` | 50% of a frozen regional catalogue | No | Frozen catalogue and biome/region denominator |

Badge awards are cosmetic and grant `0 XP` in this proposal. Streaks also grant `0 XP`; their recurrence, grace period and timezone behavior must be reviewed before implementation.

## 7. Event identity and lifecycle

Suggested idempotency keys:

```text
observation:<observation UUID>
first_species:<collection taxon ID>
research_grade:<observation UUID>
regional_discovery:<region key>:<catalogue version>:<collection taxon ID>
regional_rarity:<region key>:<catalogue version>:<collection taxon ID>
regional_legend:<region key>:<catalogue version>:<collection taxon ID>
regional_essentials:<region key>:<catalogue version>
regional_icons:<region key>:<catalogue version>
identification_given:<identification ID>
anomaly_confirmed:<observation UUID>:<range rules version>
```

`observation:<UUID>` is the existing stored key for the logical `confirmed_observation` event and must remain stable; renaming it would risk duplicate awards on upgrade.

- Relinking the same immutable iNaturalist user ID resumes its existing ledger.
- Linking a different user ID shows that user's independent progression.
- Unlinking hides personal progression but does not silently erase it; explicit local-data deletion is a separate privacy action.
- A taxon swap may change collection placement but does not delete old XP events.
- A rules update recalculates levels from lifetime XP but does not mutate existing event amounts.
- Catalogue updates recalculate visible completion but never revoke historical regional XP or an earned versioned achievement.

## 8. UI contract for the current runtime

The first progression UI may show:

- current level name;
- lifetime XP;
- progress to the next level;
- XP remaining to the next level;
- recent ledger events with plain-language sources;
- selectable earned profile title.

It must not show disabled badges, rarity multipliers, streaks or “coming soon” rewards as if they were earned features. Loading, empty, unlinked and recoverable-error states are required.

## 9. Versioning and revision policy

| Field | Current value |
|---|---|
| Current runtime rules | `progression-0.2-regional-experimental` |
| Planned rules | Freeze a reviewed release version before beta |
| Intended audience | Internal development and pre-beta device testing |
| Threshold stability guarantee | None before closed beta |
| XP ledger rewrite allowed | Never |
| Highest-level preservation | Required once closed beta starts |
| Review deadline | Before progression rules are frozen for closed beta |

Implementation should centralize enabled events, XP values and thresholds in one configuration object covered by unit tests. UI code must consume the resulting projection rather than duplicate numbers or level names.

## 10. Review log

| Date | Rules version | Reviewer | Decision summary |
|---|---|---|---|
| 13 August 2026 | `progression-0.1-placeholder` | Codex proposal | Initial editable placeholder; awaiting product review |
| 21 August 2026 | `progression-0.2-regional` | Product owner direction | Keep Legendary as regional prestige, separate from encounter rarity; add region-bound discovery and checklist rewards without rewriting existing XP |
| 21 August 2026 | `progression-0.2-regional-experimental` | Internal implementation | Enabled regional discovery, rarity, Legendary and checklist rewards with higher internal-test values; values remain adjustable before release |
