#!/usr/bin/env python3
import json
import urllib.request
import urllib.parse
import os

API_KEY = "416bb7686067411157cb4a910415839e"
OUTPUT = "app/src/main/assets/toilets.json"

DATASETS = [
    (842, "Стационарные"),
    (1494, "Модульные"),
]

def fetch_dataset(dataset_id):
    cache = f"/tmp/dataset_{dataset_id}.json"
    if os.path.exists(cache):
        print(f"  Using cached {cache}")
        with open(cache, "r", encoding="utf-8") as f:
            return json.load(f)
    url = f"https://apidata.mos.ru/v1/features/{dataset_id}?api_key={API_KEY}"
    print(f"  Fetching {url}...")
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=90) as resp:
        data = json.loads(resp.read().decode())
    with open(cache, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    return data

def parse_working_hours(wh_list):
    if not wh_list:
        return "Круглосуточно"
    hours_set = set(wh.get("Hours", "").lower() for wh in wh_list)
    if hours_set == {"круглосуточно"}:
        return "Круглосуточно"
    seen = set()
    parts = []
    for wh in wh_list:
        h = wh.get("Hours", "")
        if h and h not in seen:
            seen.add(h)
            day = wh.get("DayOfWeek", "")
            parts.append(f"{day}: {h}")
    return "; ".join(parts) if parts else "Не указано"

def map_type(paid_service):
    if paid_service is None:
        return "FREE"
    s = paid_service.lower().strip()
    if s == "платный" or s.startswith("платный"):
        return "PAID"
    return "FREE"

def map_accessible(disability):
    if disability is None:
        return False
    d = disability.lower()
    return "приспособлен" in d and "не приспособлен" not in d

def make_address(attrs):
    # LocationClarification is human-friendly (e.g. "м. Спортивная, ул. Хамовнический Вал, 37")
    clarification = (attrs.get("LocationClarification") or "").strip()
    official = (attrs.get("Location") or "").strip()
    # Strip redundant country/city prefix from official address
    official_clean = official
    for prefix in ["Российская Федерация, город Москва, ", "город Москва, "]:
        if official_clean.startswith(prefix):
            official_clean = official_clean[len(prefix):]
            break
    # Also strip long district prefix like "внутригородская территория муниципальный округ X, "
    import re
    official_clean = re.sub(r"^внутригородская территория муниципальный округ [^,]+, ", "", official_clean)
    if clarification:
        return clarification
    return official_clean

all_toilets = []
toilet_id = 1

for dataset_id, label in DATASETS:
    print(f"\nDataset {dataset_id} ({label}):")
    data = fetch_dataset(dataset_id)
    features = data.get("features", [])
    print(f"  Total features: {len(features)}")

    added = 0
    skipped = 0
    for feat in features:
        attrs = feat.get("properties", {}).get("attributes", {})

        close_flag = attrs.get("CloseFlag", "действует")
        if close_flag and "действует" not in close_flag.lower():
            skipped += 1
            continue

        geom = feat.get("geometry", {})
        coords = geom.get("coordinates", [])
        if len(coords) < 2:
            skipped += 1
            continue

        lng, lat = coords[0], coords[1]
        if not (55.0 < lat < 56.5 and 36.5 < lng < 38.5):
            skipped += 1
            continue

        name = attrs.get("Name") or "Общественный туалет"
        address = make_address(attrs)
        paid_service = attrs.get("PaidService", "бесплатный")
        disability = attrs.get("DisabilityFriendly", "")
        wh_list = attrs.get("WorkingHours", [])
        working_hours = parse_working_hours(wh_list)

        all_toilets.append({
            "id": toilet_id,
            "title": name,
            "address": address,
            "lat": lat,
            "lng": lng,
            "type": map_type(paid_service),
            "accessible": map_accessible(disability),
            "rating": 0.0,
            "workingHours": working_hours,
        })
        toilet_id += 1
        added += 1

    print(f"  Added: {added}, skipped: {skipped}")

print(f"\nTotal toilets: {len(all_toilets)}")
with open(OUTPUT, "w", encoding="utf-8") as f:
    json.dump(all_toilets, f, ensure_ascii=False, indent=2)
print(f"Written to {OUTPUT}")
