"""Build a review-only territory assignment checklist from Natural Earth map units."""

from __future__ import annotations

import csv
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DBF = ROOT / "catalogues/boundaries/source/ne_10m_admin_0_map_units_5_1_1/ne_10m_admin_0_map_units.dbf"
OUTPUT = ROOT / "catalogues/review"

# Only countries explicitly named by the owner are pre-filled. Every other row needs review.
ASSIGNMENTS = {
    "ES": "mediterranean_europe", "PT": "mediterranean_europe", "IT": "mediterranean_europe",
    "GR": "mediterranean_europe", "HR": "mediterranean_europe", "AL": "mediterranean_europe",
    "ME": "mediterranean_europe", "MT": "mediterranean_europe", "CY": "mediterranean_europe",
    "FR": "temperate_europe", "DE": "temperate_europe", "BE": "temperate_europe",
    "NL": "temperate_europe", "GB": "temperate_europe", "IE": "temperate_europe",
    "CH": "temperate_europe", "AT": "temperate_europe", "CZ": "temperate_europe", "PL": "temperate_europe",
    "NO": "boreal_europe", "SE": "boreal_europe", "FI": "boreal_europe", "IS": "boreal_europe",
    "RO": "eastern_europe_steppe", "BG": "eastern_europe_steppe", "MD": "eastern_europe_steppe",
    "UA": "eastern_europe_steppe", "BY": "eastern_europe_steppe", "RU": "siberia_boreal_asia",
    "MN": "siberia_boreal_asia", "MA": "maghreb_sahara", "DZ": "maghreb_sahara",
    "TN": "maghreb_sahara", "LY": "maghreb_sahara", "EG": "maghreb_sahara", "MR": "maghreb_sahara",
    "SN": "sahel", "ML": "sahel", "BF": "sahel", "NE": "sahel", "TD": "sahel", "SD": "sahel",
    "GN": "west_central_tropical_africa", "GW": "west_central_tropical_africa", "SL": "west_central_tropical_africa",
    "LR": "west_central_tropical_africa", "CI": "west_central_tropical_africa", "GH": "west_central_tropical_africa",
    "TG": "west_central_tropical_africa", "BJ": "west_central_tropical_africa", "NG": "west_central_tropical_africa",
    "CM": "west_central_tropical_africa", "GA": "west_central_tropical_africa", "CG": "west_central_tropical_africa", "CD": "west_central_tropical_africa",
    "ET": "east_africa", "KE": "east_africa", "UG": "east_africa", "TZ": "east_africa",
    "RW": "east_africa", "BI": "east_africa", "SS": "east_africa", "AO": "southern_africa",
    "ZM": "southern_africa", "ZW": "southern_africa", "BW": "southern_africa", "NA": "southern_africa",
    "ZA": "southern_africa", "MZ": "southern_africa", "MW": "southern_africa", "MG": "madagascar_west_indian_ocean",
    "KM": "madagascar_west_indian_ocean", "MU": "madagascar_west_indian_ocean", "SC": "madagascar_west_indian_ocean",
    "TR": "middle_east_arabia", "SY": "middle_east_arabia", "IQ": "middle_east_arabia", "IL": "middle_east_arabia",
    "JO": "middle_east_arabia", "SA": "middle_east_arabia", "OM": "middle_east_arabia", "YE": "middle_east_arabia", "AE": "middle_east_arabia",
    "IR": "central_asia", "AF": "central_asia", "KZ": "central_asia", "UZ": "central_asia", "TM": "central_asia", "KG": "central_asia", "TJ": "central_asia",
    "IN": "indian_subcontinent", "PK": "indian_subcontinent", "NP": "indian_subcontinent", "BT": "indian_subcontinent", "BD": "indian_subcontinent", "LK": "indian_subcontinent",
    "CN": "temperate_east_asia", "KP": "temperate_east_asia", "KR": "temperate_east_asia", "JP": "temperate_east_asia",
    "MM": "mainland_southeast_asia", "TH": "mainland_southeast_asia", "LA": "mainland_southeast_asia", "KH": "mainland_southeast_asia", "VN": "mainland_southeast_asia", "MY": "mainland_southeast_asia", "BN": "mainland_southeast_asia", "SG": "mainland_southeast_asia",
    "ID": "insular_southeast_asia", "PH": "insular_southeast_asia", "TL": "insular_southeast_asia",
    "AU": "australasia", "PG": "australasia", "NZ": "new_zealand_pacific",
    "CA": "temperate_boreal_north_america", "US": "temperate_boreal_north_america",
    "MX": "mesoamerica", "GT": "mesoamerica", "BZ": "mesoamerica", "HN": "mesoamerica", "SV": "mesoamerica", "NI": "mesoamerica", "CR": "mesoamerica", "PA": "mesoamerica",
    "CU": "caribbean", "JM": "caribbean", "HT": "caribbean", "DO": "caribbean", "BS": "caribbean",
    "BR": "tropical_north_south_america_amazon", "CO": "tropical_north_south_america_amazon", "VE": "tropical_north_south_america_amazon", "EC": "tropical_north_south_america_amazon", "PE": "tropical_north_south_america_amazon", "GY": "tropical_north_south_america_amazon", "SR": "tropical_north_south_america_amazon",
    "BO": "andes_southern_south_america", "AR": "andes_southern_south_america", "CL": "andes_southern_south_america", "PY": "andes_southern_south_america", "UY": "andes_southern_south_america",
}

# Geography-based recommendations for records absent from the owner's original table.
# They are proposals only; disputed/indeterminate entries are intentionally excluded below.
RECOMMENDATIONS_BY_ISO = {
    "AD": "mediterranean_europe", "AM": "middle_east_arabia", "AS": "new_zealand_pacific",
    "AI": "caribbean", "AW": "caribbean", "AZ": "middle_east_arabia", "BH": "middle_east_arabia",
    "BB": "caribbean", "BJ": "west_central_tropical_africa", "BM": "temperate_boreal_north_america",
    "BV": "unsupported", "IO": "madagascar_west_indian_ocean", "VG": "caribbean", "CV": "maghreb_sahara",
    "CM": "west_central_tropical_africa", "BQ": "caribbean", "KY": "caribbean", "CF": "west_central_tropical_africa",
    "CX": "australasia", "CC": "australasia", "CG": "west_central_tropical_africa", "CK": "new_zealand_pacific",
    "CW": "caribbean", "DK": "temperate_europe", "DJ": "east_africa", "DM": "caribbean", "GQ": "west_central_tropical_africa",
    "ER": "east_africa", "EE": "boreal_europe", "FO": "boreal_europe", "FK": "andes_southern_south_america",
    "FJ": "new_zealand_pacific", "PF": "new_zealand_pacific", "TF": "unsupported", "GA": "west_central_tropical_africa",
    "GM": "sahel", "GH": "west_central_tropical_africa", "GI": "mediterranean_europe", "GL": "temperate_boreal_north_america",
    "GD": "caribbean", "GU": "new_zealand_pacific", "GG": "temperate_europe", "GN": "west_central_tropical_africa",
    "GW": "west_central_tropical_africa", "HM": "unsupported", "HK": "temperate_east_asia", "HU": "temperate_europe",
    "IM": "temperate_europe", "SJ": "boreal_europe", "JE": "temperate_europe", "KI": "new_zealand_pacific",
    "KW": "middle_east_arabia", "LV": "temperate_europe", "LB": "middle_east_arabia", "LS": "southern_africa",
    "LR": "west_central_tropical_africa", "LI": "temperate_europe", "LT": "temperate_europe", "LU": "temperate_europe",
    "MO": "temperate_east_asia", "MV": "indian_subcontinent", "MH": "new_zealand_pacific", "YT": "madagascar_west_indian_ocean",
    "FM": "new_zealand_pacific", "MC": "mediterranean_europe", "MS": "caribbean", "MP": "new_zealand_pacific",
    "NR": "new_zealand_pacific", "NC": "new_zealand_pacific", "NU": "new_zealand_pacific", "NF": "australasia",
    "MK": "mediterranean_europe", "PW": "new_zealand_pacific", "PN": "new_zealand_pacific", "PR": "caribbean",
    "QA": "middle_east_arabia", "RE": "madagascar_west_indian_ocean", "GS": "unsupported", "SH": "southern_africa",
    "LC": "caribbean", "WS": "new_zealand_pacific", "SM": "mediterranean_europe", "SL": "west_central_tropical_africa",
    "SX": "caribbean", "SK": "temperate_europe", "SI": "mediterranean_europe", "SB": "new_zealand_pacific",
    "BL": "caribbean", "MF": "caribbean", "KN": "caribbean", "PM": "temperate_boreal_north_america",
    "VC": "caribbean", "ST": "west_central_tropical_africa", "TG": "west_central_tropical_africa", "TK": "new_zealand_pacific",
    "TO": "new_zealand_pacific", "TT": "caribbean", "TC": "caribbean", "TV": "new_zealand_pacific",
    "VI": "caribbean", "VU": "new_zealand_pacific", "VA": "mediterranean_europe", "WF": "new_zealand_pacific",
    "SZ": "southern_africa", "AX": "boreal_europe",
}
RECOMMENDATIONS_BY_NAME = {
    "Adjara": "middle_east_arabia", "Akrotiri": "mediterranean_europe", "Antigua": "caribbean",
    "Ashmore and Cartier Is.": "australasia", "Azores": "mediterranean_europe", "Baikonur": "central_asia",
    "Barbuda": "caribbean", "Bougainville": "australasia", "Brcko District": "mediterranean_europe",
    "Baker I.": "new_zealand_pacific", "Brussels": "temperate_europe", "Canary Is.": "mediterranean_europe", "Clipperton I.": "mesoamerica",
    "Cocos Is.": "australasia", "Coral Sea Is.": "australasia", "Cyprus U.N. Buffer Zone": "mediterranean_europe",
    "Dhekelia": "mediterranean_europe", "England": "temperate_europe", "Fed. of Bos. & Herz.": "mediterranean_europe",
    "Flemish": "temperate_europe", "France": "temperate_europe", "French Guiana": "tropical_north_south_america_amazon", "Georgia": "middle_east_arabia",
    "Guadeloupe": "caribbean", "Iraq": "middle_east_arabia", "Iraqi Kurdistan": "middle_east_arabia",
    "Howland I.": "new_zealand_pacific", "Jan Mayen I.": "boreal_europe", "Jarvis I.": "new_zealand_pacific",
    "Johnston Atoll": "new_zealand_pacific", "Korean DMZ (north)": "temperate_east_asia", "Korean DMZ (south)": "temperate_east_asia",
    "Madeira": "mediterranean_europe", "Martinique": "caribbean", "Midway Is.": "new_zealand_pacific",
    "N. Cyprus": "mediterranean_europe", "N. Ireland": "temperate_europe", "Netherlands": "temperate_europe",
    "Papua New Guinea": "australasia", "Palmyra Atoll": "new_zealand_pacific", "Portugal": "mediterranean_europe",
    "Puntland": "east_africa", "Rep. Srpska": "mediterranean_europe", "Scotland": "temperate_europe",
    "Serbia": "eastern_europe_steppe", "Somalia": "east_africa", "Somaliland": "east_africa",
    "Kingman Reef": "new_zealand_pacific", "Navassa I.": "caribbean", "Réunion": "madagascar_west_indian_ocean", "Syria": "middle_east_arabia", "Taiwan": "temperate_east_asia", "UNDOF Zone": "middle_east_arabia", "USNB Guantanamo Bay": "caribbean", "Wake Atoll": "new_zealand_pacific",
    "Vojvodina": "eastern_europe_steppe", "Wales": "temperate_europe", "Walloon": "temperate_europe",
    "Zanzibar": "east_africa",
}
DISPUTED_RECOMMENDATIONS = {
    "Antarctica": "unsupported", "Bajo Nuevo Bank": "marine_worldwide", "Bir Tawil": "maghreb_sahara",
    "Brazilian I.": "marine_worldwide", "Cyprus U.N. Buffer Zone": "mediterranean_europe",
    "Gaza": "middle_east_arabia", "Israel": "middle_east_arabia", "Korean DMZ (north)": "temperate_east_asia",
    "Korean DMZ (south)": "temperate_east_asia", "Kosovo": "eastern_europe_steppe",
    "Falkland Is.": "andes_southern_south_america", "Mayotte": "madagascar_west_indian_ocean",
    "Paracel Is.": "marine_worldwide", "Scarborough Reef": "marine_worldwide", "Serranilla Bank": "marine_worldwide",
    "Siachen Glacier": "indian_subcontinent", "Southern Patagonian Ice Field": "andes_southern_south_america",
    "Spratly Is.": "marine_worldwide", "W. Sahara": "maghreb_sahara", "West Bank": "middle_east_arabia",
}


def dbf_rows(path: Path):
    with path.open("rb") as handle:
        header = handle.read(32)
        count = struct.unpack("<I", header[4:8])[0]
        header_length = struct.unpack("<H", header[8:10])[0]
        record_length = struct.unpack("<H", header[10:12])[0]
        fields = []
        while handle.tell() < header_length:
            descriptor = handle.read(32)
            if not descriptor or descriptor[0] == 0x0D:
                break
            name = descriptor[:11].split(b"\0", 1)[0].decode("ascii")
            fields.append((name, chr(descriptor[11]), descriptor[16]))
        handle.seek(header_length)
        for _ in range(count):
            record = handle.read(record_length)
            if len(record) != record_length or record[0:1] == b"*":
                continue
            row, offset = {}, 1
            for name, kind, length in fields:
                value = record[offset:offset + length].decode("utf-8", errors="replace").strip(" \x00")
                row[name] = value
                offset += length
            yield row


def main() -> None:
    if not DBF.exists():
        raise SystemExit(f"Missing Natural Earth source: {DBF.relative_to(ROOT)}")
    units = []
    seen = set()
    for row in dbf_rows(DBF):
        name = row.get("NAME", row.get("ADMIN", "Unnamed map unit"))
        iso = row.get("ISO_A2", "").upper()
        key = (name, iso, row.get("GU_A3", ""))
        if key in seen:
            continue
        seen.add(key)
        country_level = row.get("TYPE", "") in {"Country", "Sovereign country", "Sovereignty"}
        owner_assignment = ASSIGNMENTS.get(iso, "") if country_level else ""
        proposed = owner_assignment or RECOMMENDATIONS_BY_NAME.get(name, "") or RECOMMENDATIONS_BY_ISO.get(iso, "")
        disputed = name in DISPUTED_RECOMMENDATIONS
        if disputed:
            proposed = DISPUTED_RECOMMENDATIONS[name]
        if owner_assignment:
            review = "pre-filled from owner table"
        elif disputed:
            review = "proposal pending disputed-boundary approval"
        elif proposed == "unsupported":
            review = "proposal: unsupported for regional progression"
        elif proposed:
            review = "proposal pending owner approval"
        else:
            review = "owner review required"
        units.append({
            "name": name,
            "iso": iso if len(iso) == 2 else "—",
            "type": row.get("TYPE", "—"),
            "sovereign": row.get("SOVEREIGNT", "—"),
            "proposed_region": proposed,
            "review": review,
        })
    units.sort(key=lambda row: (row["proposed_region"] == "", row["proposed_region"], row["name"]))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with (OUTPUT / "territory_assignment_checklist.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=units[0].keys())
        writer.writeheader()
        writer.writerows(units)
    owner_count = sum(row["review"] == "pre-filled from owner table" for row in units)
    proposal_count = sum(row["review"] == "proposal pending owner approval" for row in units)
    disputed_count = sum(row["review"] == "proposal pending disputed-boundary approval" for row in units)
    unsupported_count = sum(row["review"] == "proposal: unsupported for regional progression" for row in units)
    unresolved_count = sum(row["review"] == "owner review required" for row in units)
    lines = [
        "# Territory assignment review checklist",
        "",
        "Source: Natural Earth Admin 0 Map Units 1:10m v5.1.1 (Public Domain).",
        "This is a review worksheet, not runtime assignment data. Pre-filled rows come only from",
        "the owner's supplied country list; blank rows require an explicit region or an approved",
        "unsupported status before `country_territory_assignments.yaml` can become complete.",
        "",
        f"Map units: {len(units)} · owner pre-filled: {owner_count} · geographic proposals: {proposal_count} · disputed proposals: {disputed_count} · unsupported: {unsupported_count} · no recommendation: {unresolved_count}",
        "",
        "| Map unit | ISO | Type | Sovereign | Proposed region | Review status |",
        "|---|---|---|---|---|---|",
    ]
    lines += [f"| {row['name']} | {row['iso']} | {row['type']} | {row['sovereign']} | {row['proposed_region'] or '—'} | {row['review']} |" for row in units]
    (OUTPUT / "territory_assignment_checklist.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(units)} map-unit review rows; {owner_count} owner assignments and {proposal_count + disputed_count + unsupported_count} recommendations.")


if __name__ == "__main__":
    main()
