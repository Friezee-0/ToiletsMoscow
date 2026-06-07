#!/usr/bin/env python3
"""
Fetch Moscow toilets from:
  1. data.mos.ru (official city data — datasets 842 + 1494)
  2. Yandex Geosearch API
  3. 2GIS Catalog API (type=attraction)
  4. OpenStreetMap via Overpass API (free, no key)
Deduplicates by proximity (< 40m = same toilet).
"""

import json, urllib.request, urllib.parse, time, math, os, sys

YANDEX_KEY = "ccd4696e-de0c-413f-b314-331abc4afcab"
GIS2_KEY   = "b79bba54-89ae-4548-be26-5302b07c6b4b"
OUTPUT     = "app/src/main/assets/toilets.json"

# Moscow bounding box
LAT_MIN, LAT_MAX = 55.49, 55.93
LNG_MIN, LNG_MAX = 36.82, 38.00

def haversine(lat1, lng1, lat2, lng2):
    R = 6_371_000
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

def fetch_url(url, headers=None, timeout=60):
    req = urllib.request.Request(url, headers=headers or {"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())

def safe_fetch(url, headers=None, retries=3, delay=2, timeout=60):
    for i in range(retries):
        try:
            return fetch_url(url, headers, timeout=timeout)
        except Exception as e:
            if i < retries - 1:
                print(f"    Retry {i+1}/{retries}: {e}")
                time.sleep(delay)
            else:
                print(f"    Failed: {e}")
                return None

# ── Grid helpers ────────────────────────────────────────────────────────────
def grid_centers(lat_min, lat_max, lng_min, lng_max, lat_step, lng_step):
    """Generate (lat, lng) cell centers."""
    lat = lat_min + lat_step / 2
    while lat <= lat_max:
        lng = lng_min + lng_step / 2
        while lng <= lng_max:
            yield (lat, lng)
            lng += lng_step
        lat += lat_step

# ── Deduplication ──────────────────────────────────────────────────────────
DEDUP_RADIUS = 40  # metres — closer than this = same toilet

def is_duplicate(lat, lng, seen, radius=DEDUP_RADIUS):
    for s_lat, s_lng in seen:
        if haversine(lat, lng, s_lat, s_lng) < radius:
            return True
    return False

def add_unique(toilets, seen_coords, entry):
    lat, lng = entry["lat"], entry["lng"]
    if not is_duplicate(lat, lng, seen_coords):
        toilets.append(entry)
        seen_coords.append((lat, lng))
        return True
    return False

def in_moscow(lat, lng):
    return LAT_MIN <= lat <= LAT_MAX and LNG_MIN <= lng <= LNG_MAX

# ═══════════════════════════════════════════════════════════════════════════
# SOURCE 1 — data.mos.ru (already fetched, reuse cached files)
# ═══════════════════════════════════════════════════════════════════════════

def parse_wh(wh_list):
    if not wh_list: return "Круглосуточно"
    hours_set = {w.get("Hours","").lower() for w in wh_list}
    if hours_set == {"круглосуточно"}: return "Круглосуточно"
    seen, parts = set(), []
    for w in wh_list:
        h = w.get("Hours","")
        if h and h not in seen:
            seen.add(h); parts.append(f"{w.get('DayOfWeek','')}: {h}")
    return "; ".join(parts) or "Не указано"

def mos_type(ps):
    return "PAID" if ps and ps.lower().strip() == "платный" else "FREE"

def mos_accessible(d):
    d = (d or "").lower()
    return "приспособлен" in d and "не приспособлен" not in d

def clean_address(attrs):
    cl = (attrs.get("LocationClarification") or "").strip()
    loc = (attrs.get("Location") or "").strip()
    import re
    loc = re.sub(r"^Российская Федерация, город Москва, ", "", loc)
    loc = re.sub(r"^город Москва, ", "", loc)
    loc = re.sub(r"^внутригородская территория муниципальный округ [^,]+, ", "", loc)
    return cl if cl else loc

def load_mos_dataset(dataset_id, mobile):
    cache = f"/tmp/dataset_{dataset_id}.json"
    if not os.path.exists(cache):
        url = f"https://apidata.mos.ru/v1/features/{dataset_id}?api_key=416bb7686067411157cb4a910415839e"
        print(f"  Fetching data.mos.ru dataset {dataset_id}…")
        data = safe_fetch(url, timeout=90)
        if data:
            with open(cache,"w",encoding="utf-8") as f: json.dump(data,f,ensure_ascii=False)
    else:
        with open(cache,encoding="utf-8") as f: data = json.load(f)
    if not data: return []
    out = []
    for feat in data.get("features",[]):
        attrs = feat.get("properties",{}).get("attributes",{})
        if "действует" not in (attrs.get("CloseFlag","действует") or "действует").lower(): continue
        coords = feat.get("geometry",{}).get("coordinates",[])
        if len(coords)<2: continue
        lng, lat = coords[0], coords[1]
        if not in_moscow(lat, lng): continue
        out.append({
            "lat": lat, "lng": lng,
            "title": attrs.get("Name","Общественный туалет"),
            "address": clean_address(attrs),
            "type": mos_type(attrs.get("PaidService")),
            "mobile": mobile,
            "accessible": mos_accessible(attrs.get("DisabilityFriendly","")),
            "rating": 0.0,
            "workingHours": parse_wh(attrs.get("WorkingHours",[])),
            "source": "mos",
        })
    return out

# ═══════════════════════════════════════════════════════════════════════════
# SOURCE 2 — Yandex Geosearch
# ═══════════════════════════════════════════════════════════════════════════

YANDEX_HEADERS = {
    "Accept": "application/json",
    "Origin": "https://yandex.ru",
    "Referer": "https://yandex.ru/",
}

def yandex_cell(lat, lng, spn_lat=0.10, spn_lng=0.15):
    """Fetch one grid cell, returns list of toilet dicts."""
    results = []
    skip = 0
    while True:
        params = urllib.parse.urlencode({
            "apikey": YANDEX_KEY,
            "text": "туалет",
            "type": "biz",
            "lang": "ru_RU",
            "ll": f"{lng},{lat}",
            "spn": f"{spn_lng},{spn_lat}",
            "results": 500,
            "skip": skip,
        })
        data = safe_fetch(f"https://search-maps.yandex.ru/v1/?{params}", YANDEX_HEADERS)
        if not data: break
        feats = data.get("features", [])
        if not feats: break
        for f in feats:
            g = f.get("geometry",{})
            coords = g.get("coordinates",[])
            if len(coords) < 2: continue
            f_lng, f_lat = coords[0], coords[1]
            if not in_moscow(f_lat, f_lng): continue
            p = f.get("properties",{})
            cm = p.get("CompanyMetaData",{})
            name = p.get("name","Туалет")
            addr = cm.get("address","") or p.get("description","")
            # Hours
            hours_data = cm.get("Hours",{})
            if isinstance(hours_data, dict):
                wh = hours_data.get("text","") or hours_data.get("Availabilities","")
                if isinstance(wh, list): wh = "; ".join(str(x) for x in wh)
            else:
                wh = str(hours_data) if hours_data else ""
            t_type = "FREE" if "бесплатн" in name.lower() else "PAID"
            results.append({
                "lat": f_lat, "lng": f_lng,
                "title": name, "address": addr,
                "type": t_type, "mobile": False,
                "accessible": False, "rating": 0.0,
                "workingHours": wh or "Не указано",
                "source": "yandex",
            })
        # Yandex returns max 500, skip not always supported; stop if < 500
        if len(feats) < 500: break
        skip += 500
        time.sleep(0.3)
    return results

def fetch_yandex():
    print("\nYandex Geosearch — grid 4×5…")
    all_results = []
    cells = list(grid_centers(LAT_MIN, LAT_MAX, LNG_MIN, LNG_MAX, 0.11, 0.30))
    for i, (lat, lng) in enumerate(cells):
        print(f"  Cell {i+1}/{len(cells)}: ({lat:.3f}, {lng:.3f})", end=" ")
        r = yandex_cell(lat, lng, spn_lat=0.07, spn_lng=0.17)
        print(f"→ {len(r)}")
        all_results.extend(r)
        time.sleep(0.4)
    return all_results

# ═══════════════════════════════════════════════════════════════════════════
# SOURCE 3 — 2GIS
# ═══════════════════════════════════════════════════════════════════════════

def gis2_cell(lat, lng, radius=14000):
    results = []
    page = 1
    while True:
        # page_size max = 10 in 2GIS free tier
        params = urllib.parse.urlencode({
            "q": "туалет",
            "type": "attraction",
            "location": f"{lng},{lat}",
            "radius": radius,
            "page_size": 10,
            "page": page,
            "fields": "items.point,items.rubrics",
            "key": GIS2_KEY,
        })
        data = safe_fetch(f"https://catalog.api.2gis.com/3.0/items?{params}")
        if not data: break
        meta_code = data.get("meta", {}).get("code", 200)
        if meta_code != 200: break
        items = data.get("result",{}).get("items",[])
        if not items: break
        for it in items:
            pt = it.get("point")
            if not pt: continue
            f_lat, f_lng = pt["lat"], pt["lon"]
            if not in_moscow(f_lat, f_lng): continue
            rubrics = [r.get("name","") for r in it.get("rubrics",[])]
            if not any("туалет" in r.lower() for r in rubrics): continue
            name = it.get("name") or "Туалет"
            addr = it.get("address_name","")
            t_type = "PAID" if name and any(w in name.lower() for w in ["платн"]) else "FREE"
            results.append({
                "lat": f_lat, "lng": f_lng,
                "title": name, "address": addr,
                "type": t_type, "mobile": False,
                "accessible": False, "rating": 0.0,
                "workingHours": "Не указано",
                "source": "2gis",
            })
        total = data.get("result",{}).get("total", 0)
        if page * 10 >= min(total, 500) or page >= 50: break
        page += 1
        time.sleep(0.25)
    return results

def fetch_2gis():
    print("\n2GIS — grid 4×4…")
    all_results = []
    cells = list(grid_centers(LAT_MIN, LAT_MAX, LNG_MIN, LNG_MAX, 0.12, 0.30))
    for i, (lat, lng) in enumerate(cells):
        print(f"  Cell {i+1}/{len(cells)}: ({lat:.3f}, {lng:.3f})", end=" ")
        r = gis2_cell(lat, lng)
        print(f"→ {len(r)}")
        all_results.extend(r)
        time.sleep(0.4)
    return all_results

# ═══════════════════════════════════════════════════════════════════════════
# SOURCE 4 — OpenStreetMap (Overpass API)
# ═══════════════════════════════════════════════════════════════════════════

def fetch_osm():
    print("\nOpenStreetMap (Overpass API)…")
    query = """[out:json][timeout:120];
(node["amenity"="toilets"](55.49,36.82,55.93,38.00);
way["amenity"="toilets"](55.49,36.82,55.93,38.00););
out center;"""
    body = urllib.parse.urlencode({"data": query}).encode()
    req = urllib.request.Request(
        "https://overpass-api.de/api/interpreter",
        data=body,
        headers={"User-Agent": "ToiletsMoscow/1.0", "Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    data = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = json.loads(r.read().decode())
            break
        except Exception as e:
            print(f"  OSM retry {attempt+1}: {e}")
            time.sleep(5)
    if data is None:
        # Try alternative Overpass instance
        req2 = urllib.request.Request(
            "https://overpass.kumi.systems/api/interpreter",
            data=body,
            headers={"User-Agent": "ToiletsMoscow/1.0", "Content-Type": "application/x-www-form-urlencoded"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req2, timeout=120) as r:
                data = json.loads(r.read().decode())
        except Exception as e:
            print(f"  OSM fallback failed: {e}")
            return []
    if not data: return []
    results = []
    for el in data.get("elements",[]):
        if el.get("type") == "way":
            lat = el.get("center",{}).get("lat")
            lng = el.get("center",{}).get("lon")
        else:
            lat = el.get("lat"); lng = el.get("lon")
        if not lat or not lng: continue
        if not in_moscow(lat, lng): continue
        tags = el.get("tags",{})
        name = tags.get("name") or tags.get("description") or "Туалет (OSM)"
        fee = tags.get("fee","")
        t_type = "PAID" if fee.lower() in ("yes","платный") else "FREE"
        accessible = tags.get("wheelchair","") in ("yes","limited","designated")
        addr = tags.get("addr:street","")
        if addr and tags.get("addr:housenumber"):
            addr += ", " + tags["addr:housenumber"]
        hours = tags.get("opening_hours","Не указано")
        results.append({
            "lat": lat, "lng": lng,
            "title": name, "address": addr,
            "type": t_type, "mobile": False,
            "accessible": accessible, "rating": 0.0,
            "workingHours": hours,
            "source": "osm",
        })
    print(f"  OSM: {len(results)} toilets")
    return results

# ═══════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════

print("=== Fetching Moscow toilet data from all sources ===\n")

# Load from data.mos.ru (priority source, highest accuracy)
print("data.mos.ru — dataset 842 (stationary):")
mos_stationary = load_mos_dataset(842, mobile=False)
print(f"  {len(mos_stationary)} active toilets")

print("data.mos.ru — dataset 1494 (modular):")
mos_modular = load_mos_dataset(1494, mobile=True)
print(f"  {len(mos_modular)} active toilets")

# Fetch from external sources
yandex_raw = fetch_yandex()
print(f"  Yandex raw: {len(yandex_raw)}")

gis2_raw = fetch_2gis()
print(f"  2GIS raw: {len(gis2_raw)}")

osm_raw = fetch_osm()

# ── Merge with deduplication ───────────────────────────────────────────────
print("\nMerging and deduplicating…")
toilets = []
seen = []  # list of (lat, lng) already added

# Priority order: mos.ru first (most accurate), then Yandex, 2GIS, OSM
for entry in mos_stationary + mos_modular:
    add_unique(toilets, seen, entry)
mos_count = len(toilets)
print(f"  After data.mos.ru: {mos_count}")

yandex_added = 0
for entry in yandex_raw:
    if add_unique(toilets, seen, entry): yandex_added += 1
print(f"  After Yandex (+{yandex_added} new): {len(toilets)}")

gis2_added = 0
for entry in gis2_raw:
    if add_unique(toilets, seen, entry): gis2_added += 1
print(f"  After 2GIS (+{gis2_added} new): {len(toilets)}")

osm_added = 0
for entry in osm_raw:
    if add_unique(toilets, seen, entry): osm_added += 1
print(f"  After OSM (+{osm_added} new): {len(toilets)}")

# Assign final IDs, remove source field
final = []
for i, t in enumerate(toilets, 1):
    t.pop("source", None)
    t["id"] = i
    final.append(t)

print(f"\nTotal unique toilets: {len(final)}")
print(f"  Types: FREE={sum(1 for t in final if t['type']=='FREE')}, PAID={sum(1 for t in final if t['type']=='PAID')}")
print(f"  Accessible: {sum(1 for t in final if t['accessible'])}")
print(f"  Mobile: {sum(1 for t in final if t['mobile'])}")

with open(OUTPUT, "w", encoding="utf-8") as f:
    json.dump(final, f, ensure_ascii=False, indent=2)
print(f"Written to {OUTPUT}")
