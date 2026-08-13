# Wildlife Progression Rules — Placeholder v0.1

**Status:** Implemented internal placeholder; requires product review before closed beta  
**Date:** 13 August 2026  
**Authority:** `Wildlife_prd.md` remains authoritative. This file makes its incomplete progression rules explicit and editable.

**Catalan review copy:** [`progression_rules_ca.md`](progression_rules_ca.md)

**Implementation:** `ProgressionRules.kt` is the centralized runtime configuration for this version. SQLite schema v4 preserves existing event keys and points while adding typed history metadata. Profile shows the projected level, progress, recent rewards and selectable earned titles. Disabled mechanics in this document are not shown or awarded.

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
- Historical observations imported during initial account sync unlock the collection but do not earn retroactive XP in this placeholder version.
- An ambiguous handoff earns nothing until the user explicitly confirms the matching public observation.

## 3. XP event configuration

These keys and values are the editable source for the placeholder implementation.

| Event key | Trigger | XP | Enabled | Notes |
|---|---|---:|---|---|
| `confirmed_observation` | A matched public observation is explicitly confirmed in Wildlife | 10 | Yes | Matches the current ledger |
| `first_species` | The user's first confirmed species-level collection taxon | 500 | Yes | Matches the current ledger; keyed by collection taxon ID |
| `research_grade` | A previously known public observation first reaches Research Grade | 50 | No | Enable only when transition sync and idempotency are implemented |
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

`first_species` and a future `research_grade` event are independent of this repeat schedule. An unidentified or genus-level observation may earn base observation XP, but it earns `first_species` only if a later sync supplies a species-level collection taxon and no corresponding first-species event already exists.

### 3.2 Rarity configuration

Rarity multipliers are disabled while the catalogue and seasonal rarity model are provisional.

| Rarity tier | Proposed multiplier on base observation XP | Enabled |
|---|---:|---|
| Common | ×1 | No |
| Uncommon | ×5 | No |
| Rare | ×20 | No |
| Legendary | ×50 | No |

Before enabling these values, validate the seasonal catalogue rules and simulate whether rarity rewards overwhelm first-species progression. Raw observation frequency must not be presented as biological rarity.

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

### 4.2 Placeholder pacing examples

Assuming one rewarded observation for each new species and no other XP:

| New species confirmed | Approximate XP | Resulting level |
|---:|---:|---|
| 0 | 0 | Tourist |
| 1 | 510 | Explorer |
| 5 | 2,550 | Naturalist |
| 15 | 7,650 | Tracker |
| 40 | 20,400 | Field Ranger |
| 99 | 50,490 | Master Ranger |
| 197 | 100,470 | Legendary Ranger |

These examples must be simulated against representative small, medium and highly active accounts before beta.

## 5. Rewards and unlocks

The placeholder grants profile titles only. A user may display any title at or below the highest level they have reached.

| Reward type | Placeholder rule |
|---|---|
| Profile title | Enabled; one title per reached level |
| Themes or colour palettes | Disabled |
| Special catalogue access | Prohibited; biological information is never level-gated |
| XP boosters or multipliers | Prohibited |
| Competitive advantage | Prohibited |
| Physical or monetary reward | Out of scope |

If thresholds change after beta begins, an account must not lose an already reached title. Store or derive `highest_level_achieved` during the migration before raising any threshold.

## 6. Badge and streak configuration

Badges and streaks remain disabled for MVP, matching the roadmap's v1.1 scope. The following are review candidates, not implementation commitments:

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
identification_given:<identification ID>
anomaly_confirmed:<observation UUID>:<range rules version>
```

`observation:<UUID>` is the existing stored key for the logical `confirmed_observation` event and must remain stable; renaming it would risk duplicate awards on upgrade.

- Relinking the same immutable iNaturalist user ID resumes its existing ledger.
- Linking a different user ID shows that user's independent progression.
- Unlinking hides personal progression but does not silently erase it; explicit local-data deletion is a separate privacy action.
- A taxon swap may change collection placement but does not delete old XP events.
- A rules update recalculates levels from lifetime XP but does not mutate existing event amounts.

## 8. UI contract for the placeholder

The first progression UI may show:

- current level name;
- lifetime XP;
- progress to the next level;
- XP remaining to the next level;
- recent ledger events with plain-language sources;
- selectable earned profile title.

It must not show disabled badges, rarity multipliers, streaks or “coming soon” rewards as if they were earned features. Loading, empty, unlinked and recoverable-error states are required.

## 9. Versioning and revision policy

| Field | Placeholder value |
|---|---|
| Rules version | `progression-0.1-placeholder` |
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
