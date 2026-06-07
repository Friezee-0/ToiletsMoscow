#!/usr/bin/env python3
import json
import urllib.request
import sys

API_KEY = "416bb7686067411157cb4a910415839e"
URL = f"https://apidata.mos.ru/v1/features/842?api_key={API_KEY}"
OUTPUT = "app/src/main/assets/toilets.json"

def parse_working_hours(wh_list):
    if not wh_list:
        return "Круглосуточно"
    # Check if all days are "круглосуточно"
    hours_set = set(wh.get("Hours", "").lower() for wh in wh_list)
    if hours_set == {"круглосуточно"}:
        return "Круглосуточно"
    # Group unique time ranges
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
    # "бесплатный" contains "платный" — check specifically for "платный" alone
    if s == "платный" or s.startswith("платный"):
        return "PAID"
    return "FREE"

def map_accessible(disability):
    if disability is None:
        return False
    d = disability.lower()
    return "приспособлен" in d and "не приспособлен" not in d

# Use cached file if available, otherwise fetch
import os
if os.path.exists("/tmp/test_api.json"):
    print("Using cached API response from /tmp/test_api.json")
    with open("/tmp/test_api.json", "r", encoding="utf-8") as f:
        data = json.load(f)
else:
    print(f"Fetching from {URL}...")
    req = urllib.request.Request(URL, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read().decode())

features = data.get("features", [])
print(f"Total features: {len(features)}")

toilets = []
skipped = 0

for i, feat in enumerate(features):
    attrs = feat.get("properties", {}).get("attributes", {})

    # Skip closed toilets
    close_flag = attrs.get("CloseFlag", "действует")
    if close_flag and "действует" not in close_flag.lower():
        skipped += 1
        continue

    geom = feat.get("geometry", {})
    coords = geom.get("coordinates", [])
    if len(coords) < 2:
        skipped += 1
        continue

    lng = coords[0]
    lat = coords[1]

    # Sanity check - Moscow area
    if not (55.0 < lat < 56.5 and 36.5 < lng < 38.5):
        skipped += 1
        continue

    name = attrs.get("Name", "Общественный туалет")
    address = attrs.get("Location", "")
    paid_service = attrs.get("PaidService", "бесплатный")
    disability = attrs.get("DisabilityFriendly", "")
    wh_list = attrs.get("WorkingHours", [])
    working_hours = parse_working_hours(wh_list)

    toilets.append({
        "id": i + 1,
        "title": name,
        "address": address,
        "lat": lat,
        "lng": lng,
        "type": map_type(paid_service),
        "accessible": map_accessible(disability),
        "rating": 0.0,
        "workingHours": working_hours,
    })

print(f"Active toilets: {len(toilets)}, skipped: {skipped}")

with open(OUTPUT, "w", encoding="utf-8") as f:
    json.dump(toilets, f, ensure_ascii=False, indent=2)

print(f"Written to {OUTPUT}")
