"""Generate a self-contained, read-only visual review page for published catalogue media."""

from __future__ import annotations

import argparse
import html
import json
from datetime import datetime, timezone
from pathlib import Path

from tools.audit_silhouette_refresh import CLASS_GROUPS, audit, published_taxa, silhouette_role


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "catalogues" / "review" / "catalogue-media-review.html"


def variant_url(asset: dict | None, preferred: str = "thumbnail") -> str | None:
    if not asset:
        return None
    variants = asset.get("variants", [])
    selected = next((row for row in variants if row.get("variant") == preferred), None)
    selected = selected or (variants[0] if variants else None)
    return selected.get("direct_url") if isinstance(selected, dict) else None


def build_rows(root: Path, silhouette_audit: dict) -> tuple[list[dict], dict]:
    catalogues = root / "catalogues"
    published, regions = published_taxa(catalogues)
    taxa_doc = json.loads((catalogues / "taxa.yaml").read_text(encoding="utf-8"))
    taxa = {row["taxon_id"]: row for row in taxa_doc.get("taxa", []) if row.get("taxon_id") in published}
    manifest = json.loads((catalogues / "media_manifest.yaml").read_text(encoding="utf-8"))
    generated_report_path = catalogues / "generated" / "catalogue-report.json"
    generated_report = json.loads(generated_report_path.read_text(encoding="utf-8")) if generated_report_path.is_file() else {}

    assets: dict[int, dict[str, dict]] = {}
    for asset in manifest.get("assets", []):
        taxon_id = asset.get("taxon_id")
        if taxon_id not in published:
            continue
        role = "photo" if asset.get("media_type") == "photo" else silhouette_role(asset)
        assets.setdefault(taxon_id, {})[role] = asset
    audit_results = {
        (row["taxon_id"], row["role"]): row["reason"]
        for row in silhouette_audit.get("details", [])
    }
    taxon_regions: dict[int, list[str]] = {taxon_id: [] for taxon_id in published}
    for region, taxon_ids in regions.items():
        for taxon_id in taxon_ids:
            taxon_regions.setdefault(taxon_id, []).append(region)

    rows = []
    for taxon_id in sorted(published):
        taxon = taxa.get(taxon_id)
        if not taxon:
            continue
        assigned = assets.get(taxon_id, {})
        taxonomy = taxon.get("taxonomy", {})
        group = CLASS_GROUPS.get(taxonomy.get("class"), "other")
        photo = assigned.get("photo")

        def media(role: str) -> dict | None:
            asset = assigned.get(role)
            if not asset:
                return None
            return {
                "url": variant_url(asset),
                "source_url": asset.get("source_url"),
                "provider": asset.get("provider"),
                "creator": asset.get("creator") or asset.get("resolved_creator"),
                "licence": asset.get("licence_code"),
                "match_rank": asset.get("match_rank"),
                "matched_name": asset.get("matched_taxon_name"),
            }

        rows.append({
            "taxon_id": taxon_id,
            "common_name": taxon.get("common_names", {}).get("en") or taxon.get("scientific_name"),
            "scientific_name": taxon.get("scientific_name"),
            "group": group,
            "family": taxonomy.get("family") or "Unknown",
            "regions": sorted(taxon_regions.get(taxon_id, [])),
            "photo": media("photo"),
            "specific": media("specific"),
            "family_silhouette": media("family"),
            "specific_audit": audit_results.get((taxon_id, "specific"), "unknown"),
            "family_audit": audit_results.get((taxon_id, "family"), "unknown"),
            "fallback_url": f"../../app/src/main/assets/taxon-glyphs/{group}.svg" if group != "other" else None,
        })
    metadata = {
        "generation_id": generated_report.get("generation_id"),
        "generated_catalogue_at": generated_report.get("generated_at"),
        "review_generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "refresh_report_complete": silhouette_audit.get("refresh_report_complete", False),
        "published_taxa": len(rows),
    }
    return rows, metadata


def safe_script_json(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")


def render(rows: list[dict], metadata: dict) -> str:
    payload = safe_script_json({"metadata": metadata, "taxa": rows})
    live_label = "Complete refresh snapshot" if metadata["refresh_report_complete"] else "Provisional — refresh still incomplete"
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Wildlife catalogue media review</title>
<style>
:root{{--bg:#080b09;--surface:#0d110d;--raised:#141810;--warm:#191a11;--outline:#303126;--subtle:#22261d;--text:#f1e6cf;--secondary:#c5b99f;--muted:#8f8978;--olive:#7d8530;--olive2:#9aa23d;--gold:#d9a441;--danger:#b76b55}}
*{{box-sizing:border-box}} body{{margin:0;background:var(--bg);color:var(--text);font:14px/1.45 system-ui,sans-serif}} button,input,select{{font:inherit}} a{{color:var(--secondary)}}
header{{position:sticky;top:0;z-index:3;background:rgba(8,11,9,.96);border-bottom:1px solid var(--subtle);padding:16px clamp(16px,3vw,36px)}}
h1{{font:600 clamp(24px,3vw,36px) Georgia,serif;margin:0 0 4px}} .subtitle{{color:var(--secondary)}} .status{{display:inline-block;margin-left:8px;padding:3px 8px;border:1px solid var(--outline);border-radius:8px;color:var(--gold)}}
.controls{{display:grid;grid-template-columns:minmax(220px,2fr) repeat(4,minmax(120px,1fr));gap:8px;margin-top:14px}} input,select{{min-height:44px;background:var(--surface);color:var(--text);border:1px solid var(--outline);border-radius:9px;padding:0 10px}}
main{{padding:16px clamp(16px,3vw,36px) 90px}} .summary{{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:16px}} .stat{{background:var(--surface);border:1px solid var(--subtle);border-radius:10px;padding:8px 12px}} .stat strong{{font-size:18px}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(310px,1fr));gap:10px}} .card{{background:var(--surface);border:1px solid var(--subtle);border-radius:12px;overflow:hidden}} .card.rejected{{border-color:var(--danger)}}
.photo{{height:190px;background:#0b0e0c;display:grid;place-items:center;overflow:hidden;position:relative}} .photo img{{width:100%;height:100%;object-fit:cover}} .photo .none{{color:var(--muted)}} .identity{{padding:12px 12px 6px}} h2{{font:600 20px Georgia,serif;margin:0}} em{{color:var(--secondary)}} .meta{{color:var(--muted);font-size:12px;margin-top:4px}}
.levels{{display:grid;grid-template-columns:1fr 1fr;gap:6px;padding:6px 12px}} .level{{display:grid;grid-template-columns:54px 1fr;gap:8px;align-items:center;background:var(--raised);border-radius:8px;padding:6px;min-height:66px}} .level img{{width:54px;height:54px;object-fit:contain;filter:brightness(.78) sepia(.15)}} .level b{{display:block;font-size:11px;text-transform:uppercase;letter-spacing:.06em}} .level small{{color:var(--muted)}}
.credits{{padding:6px 12px;color:var(--secondary);font-size:12px;min-height:45px}} .actions{{display:flex;gap:8px;padding:8px 12px 12px}} button{{min-height:44px;border:1px solid var(--outline);border-radius:9px;background:var(--warm);color:var(--text);padding:0 12px;cursor:pointer}} button.primary{{background:var(--olive);border-color:var(--olive)}} button.reject{{margin-left:auto}} button.undo{{border-color:var(--danger)}} button:focus-visible,a:focus-visible,input:focus-visible,select:focus-visible{{outline:3px solid var(--gold);outline-offset:2px}}
.pager{{display:flex;justify-content:center;align-items:center;gap:12px;margin:20px}} .empty{{padding:40px;text-align:center;color:var(--secondary)}}
.reviewbar{{position:fixed;z-index:4;bottom:0;left:0;right:0;display:flex;align-items:center;gap:12px;background:var(--warm);border-top:1px solid var(--outline);padding:10px clamp(16px,3vw,36px)}} .reviewbar button{{background:var(--olive)}} .reviewbar span{{margin-right:auto}}
@media(max-width:850px){{.controls{{grid-template-columns:1fr 1fr}} .controls input{{grid-column:1/-1}}}} @media(max-width:520px){{.controls{{grid-template-columns:1fr}} .controls input{{grid-column:auto}} .grid{{grid-template-columns:1fr}}}}
</style>
</head>
<body>
<header><h1>Catalogue media review</h1><div class="subtitle">{html.escape(str(metadata.get('generation_id') or 'Unbuilt generation'))}<span class="status">{html.escape(live_label)}</span></div>
<div class="controls"><input id="search" type="search" placeholder="Search common name, scientific name, family or ID" aria-label="Search catalogue">
<select id="region" aria-label="Region"><option value="">All regions</option></select><select id="group" aria-label="Group"><option value="">All groups</option></select>
<select id="photo" aria-label="Photo coverage"><option value="">Any photo status</option><option value="yes">Has photo</option><option value="no">Missing photo</option></select>
<select id="silhouette" aria-label="Silhouette coverage"><option value="">Any silhouette status</option><option value="both">Both detailed levels</option><option value="specific">Specific only</option><option value="family">Family only</option><option value="none">Group fallback only</option><option value="incomplete">Refresh incomplete</option><option value="gap">Confirmed provider gap</option></select></div></header>
<main><div id="summary" class="summary"></div><div id="grid" class="grid"></div><div id="pager" class="pager"></div></main>
<div class="reviewbar"><span><strong id="queueCount">0</strong> image rejection(s) queued locally</span><button id="clearQueue">Clear queue</button><button id="download">Download decisions</button></div>
<script id="catalogue-data" type="application/json">{payload}</script>
<script>
const DATA=JSON.parse(document.getElementById('catalogue-data').textContent), PAGE=60, KEY='wildlife-catalogue-review-rejections-v1';
let page=0, queue=JSON.parse(localStorage.getItem(KEY)||'[]'); const $=id=>document.getElementById(id);
const escapeHtml=s=>String(s??'').replace(/[&<>"']/g,c=>({{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}}[c]));
const unique=(key,flat=false)=>[...new Set(DATA.taxa.flatMap(x=>flat?x[key]:[x[key]]).filter(Boolean))].sort();
for(const value of unique('regions',true)) $('region').insertAdjacentHTML('beforeend',`<option value="${{escapeHtml(value)}}">${{escapeHtml(value.replaceAll('_',' '))}}</option>`);
for(const value of unique('group')) $('group').insertAdjacentHTML('beforeend',`<option value="${{escapeHtml(value)}}">${{escapeHtml(value)}}</option>`);
function silhouetteState(x){{if(x.specific&&x.family_silhouette)return'both';if(x.specific)return'specific';if(x.family_silhouette)return'family';return'none'}}
function filtered(){{const q=$('search').value.trim().toLowerCase(), r=$('region').value,g=$('group').value,p=$('photo').value,s=$('silhouette').value;return DATA.taxa.filter(x=>{{
 const hay=`${{x.taxon_id}} ${{x.common_name}} ${{x.scientific_name}} ${{x.family}}`.toLowerCase(); if(q&&!hay.includes(q))return false;if(r&&!x.regions.includes(r))return false;if(g&&x.group!==g)return false;if(p==='yes'&&!x.photo)return false;if(p==='no'&&x.photo)return false;
 if(['both','specific','family','none'].includes(s)&&silhouetteState(x)!==s)return false;if(s==='incomplete'&&!x.specific_audit.includes('incomplete')&&!x.family_audit.includes('incomplete'))return false;if(s==='gap'&&x.specific_audit!=='cached_clean_no_match'&&x.family_audit!=='cached_clean_no_match')return false;return true}})}}
function mediaLevel(label,m,fallback,audit){{const src=m?.url||fallback||'';const detail=m?`${{m.match_rank||''}} · ${{m.matched_name||''}}`:(audit==='cached_clean_no_match'?'Provider no-match':audit.includes('incomplete')?'Refresh incomplete':'Group fallback');return `<div class="level">${{src?`<img loading="lazy" src="${{escapeHtml(src)}}" alt="">`:''}}<div><b>${{label}}</b><small>${{escapeHtml(detail)}}</small></div></div>`}}
function isQueued(x){{return queue.some(r=>r.taxon_id===x.taxon_id&&r.source_url===x.photo?.source_url)}}
function card(x){{const queued=isQueued(x), photo=x.photo;return `<article class="card ${{queued?'rejected':''}}"><div class="photo">${{photo?.url?`<img loading="lazy" src="${{escapeHtml(photo.url)}}" alt="Reference image of ${{escapeHtml(x.common_name)}}">`:`<span class="none">No reference photograph</span>`}}</div><div class="identity"><h2>${{escapeHtml(x.common_name)}}</h2><em>${{escapeHtml(x.scientific_name)}}</em><div class="meta">#${{x.taxon_id}} · ${{escapeHtml(x.group)}} · ${{escapeHtml(x.family)}}<br>${{x.regions.map(v=>escapeHtml(v.replaceAll('_',' '))).join(' · ')}}</div></div><div class="levels">${{mediaLevel('Specific',x.specific,x.family_silhouette?.url||x.fallback_url,x.specific_audit)}}${{mediaLevel('Family',x.family_silhouette,x.fallback_url,x.family_audit)}}</div><div class="credits">${{photo?`${{escapeHtml(photo.creator||'Unknown creator')}} · ${{escapeHtml(photo.licence||'Unknown licence')}} · ${{escapeHtml(photo.provider||'')}}`:''}}</div><div class="actions">${{photo?.source_url?`<button onclick="window.open('${{escapeHtml(photo.source_url)}}','_blank','noopener')">Open source</button><button class="${{queued?'undo':'reject'}}" onclick="toggleReject(${{x.taxon_id}})">${{queued?'Undo rejection':'Reject image'}}</button>`:''}}</div></article>`}}
function render(){{const rows=filtered(),pages=Math.max(1,Math.ceil(rows.length/PAGE));page=Math.min(page,pages-1);const shown=rows.slice(page*PAGE,(page+1)*PAGE);$('grid').innerHTML=shown.length?shown.map(card).join(''):'<div class="empty">No species match these filters.</div>';$('summary').innerHTML=`<div class="stat"><strong>${{rows.length.toLocaleString()}}</strong><br>matching taxa</div><div class="stat"><strong>${{rows.filter(x=>x.photo).length.toLocaleString()}}</strong><br>with photos</div><div class="stat"><strong>${{rows.filter(x=>x.specific).length.toLocaleString()}}</strong><br>specific silhouettes</div><div class="stat"><strong>${{rows.filter(x=>x.family_silhouette).length.toLocaleString()}}</strong><br>family silhouettes</div>`;$('pager').innerHTML=`<button ${{page===0?'disabled':''}} onclick="page--;render()">Previous</button><span>Page ${{page+1}} of ${{pages}}</span><button ${{page>=pages-1?'disabled':''}} onclick="page++;render()">Next</button>`;$('queueCount').textContent=queue.length}}
function toggleReject(id){{const x=DATA.taxa.find(v=>v.taxon_id===id);if(!x?.photo?.source_url)return;const index=queue.findIndex(r=>r.taxon_id===id&&r.source_url===x.photo.source_url);if(index>=0)queue.splice(index,1);else queue.push({{taxon_id:id,source_url:x.photo.source_url,provider:x.photo.provider,catalogue_generation_id:DATA.metadata.generation_id,reason:'authoring_review',rejected_at_ms:Date.now()}});localStorage.setItem(KEY,JSON.stringify(queue));render()}} window.toggleReject=toggleReject;
for(const id of ['search','region','group','photo','silhouette']) $(id).addEventListener('input',()=>{{page=0;render()}});$('clearQueue').onclick=()=>{{if(confirm('Clear all locally queued review decisions?')){{queue=[];localStorage.removeItem(KEY);render()}}}};$('download').onclick=()=>{{if(!queue.length)return alert('No image rejections are queued.');const out={{schema_version:1,exported_at:new Date().toISOString(),source:'Wildlife catalogue visual review',reference_media_rejections:queue}};const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([JSON.stringify(out,null,2)],{{type:'application/json'}}));a.download=`wildlife-catalogue-rejections-${{Date.now()}}.json`;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000)}};render();
</script>
</body></html>"""


def generate(root: Path = ROOT, output: Path = DEFAULT_OUTPUT) -> dict:
    silhouette_audit = audit(root)
    rows, metadata = build_rows(root, silhouette_audit)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render(rows, metadata), encoding="utf-8")
    return {"output": str(output), **metadata}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    result = generate(ROOT, args.output.resolve())
    print(f"Catalogue review generated for {result['published_taxa']:,} taxa")
    print(f"Snapshot: {'complete' if result['refresh_report_complete'] else 'provisional (refresh incomplete)'}")
    print(f"Open: {result['output']}")


if __name__ == "__main__":
    main()
