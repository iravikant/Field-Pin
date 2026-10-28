#!/usr/bin/env python3
"""Generates app/src/main/assets/mock_server_seed.json (users + 24 sample sites around Delhi).

Boundaries are hand-parameterised: a real-world centre point, approximate extents and a vertex
count. They are NOT surveyed cadastral data (see README). Run from the repo root.
"""
import hashlib, json, math, random, datetime

A = 6378137.0
F = 1 / 298.257223563
E2 = F * (2 - F)

def m_per_deg(lat):
    p = math.radians(lat)
    lat_m = 111132.92 - 559.82 * math.cos(2 * p) + 1.175 * math.cos(4 * p)
    lon_m = 111412.84 * math.cos(p) - 93.5 * math.cos(3 * p)
    return lat_m, lon_m

def shape(lat, lng, w, h, n, rot_deg, seed):
    rnd = random.Random(seed)
    lat_m, lon_m = m_per_deg(lat)
    rot = math.radians(rot_deg)
    pts = []
    for i in range(n):
        ang = 2 * math.pi * i / n + math.pi / n
        r = 1.0 if n == 4 else rnd.uniform(0.82, 1.0)
        x = math.cos(ang) * (w / 2) * r * (1.0 if n > 4 else 1.414)
        y = math.sin(ang) * (h / 2) * r * (1.0 if n > 4 else 1.414)
        if n == 4:  # slightly irregular quad
            x *= rnd.uniform(0.92, 1.0); y *= rnd.uniform(0.92, 1.0)
        xr = x * math.cos(rot) - y * math.sin(rot)
        yr = x * math.sin(rot) + y * math.cos(rot)
        pts.append({"lat": round(lat + yr / lat_m, 6), "lng": round(lng + xr / lon_m, 6)})
    return pts

# Authalic-sphere area (same algorithm as GeoMath.kt) so the printout matches the app.
def area_sqm(pts):
    e = math.sqrt(E2)
    def q(phi):
        s = math.sin(phi)
        return (1 - E2) * (s / (1 - E2 * s * s) - (1 / (2 * e)) * math.log((1 - e * s) / (1 + e * s)))
    qp = q(math.pi / 2)
    r = A * math.sqrt(qp / 2)
    def beta(latdeg):
        return math.asin(max(-1, min(1, q(math.radians(latdeg)) / qp)))
    def tri(t1, l1, t2, l2):
        d = l1 - l2; t = t1 * t2
        return 2 * math.atan2(t * math.sin(d), 1 + t * math.cos(d))
    total = 0.0
    prev = pts[-1]
    pt = math.tan((math.pi / 2 - beta(prev["lat"])) / 2); pl = math.radians(prev["lng"])
    for p in pts:
        t = math.tan((math.pi / 2 - beta(p["lat"])) / 2); l = math.radians(p["lng"])
        total += tri(pt, pl, t, l)
        pt, pl = t, l
    return abs(total) * r * r

def ms(y, m, d, hh=10, mm=30):
    tz = datetime.timezone(datetime.timedelta(hours=5, minutes=30))
    return int(datetime.datetime(y, m, d, hh, mm, tzinfo=tz).timestamp() * 1000)

# name, category, status, lat, lng, width m, height m, vertices, rotation, last inspection (d, m) or None, description
SITES = [
 ("Lodhi Garden", "PARK", "ACTIVE", 28.5931, 77.2197, 380, 620, 6, 8, (21, 10), "Heritage garden with tombs; high footfall."),
 ("Sunder Nursery", "PARK", "ACTIVE", 28.5934, 77.2438, 300, 520, 5, -12, (18, 10), "Heritage park beside Humayun's Tomb."),
 ("Nehru Park", "PARK", "NEEDS_REPAIR", 28.5965, 77.1893, 420, 520, 6, 20, (12, 10), "Jogging track resurfacing pending."),
 ("Buddha Jayanti Park", "PARK", "ACTIVE", 28.6277, 77.1873, 520, 380, 5, 5, (9, 10), "Ridge park with stupa and lawns."),
 ("Deer Park, Hauz Khas", "PARK", "ACTIVE", 28.5519, 77.2036, 480, 350, 7, -20, (2, 10), "Enclosure fencing checked quarterly."),
 ("Garden of Five Senses", "PARK", "NEEDS_REPAIR", 28.5124, 77.1985, 300, 240, 6, 0, (30, 9), "Sculpture plaza lighting faulty."),
 ("Central Park, Connaught Place", "PARK", "ACTIVE", 28.6310, 77.2170, 190, 190, 4, 45, (24, 10), "Amphitheatre and fountain."),
 ("Millennium Park", "PARK", "CLOSED", 28.6247, 77.2425, 360, 280, 5, 15, (1, 9), "Closed for landscaping works."),
 ("Indraprastha Park", "PARK", "ACTIVE", 28.6165, 77.2495, 330, 400, 6, -8, (15, 10), "Riverside park near ring road."),
 ("Ward 4 Water Tank", "WATER_TANK", "ACTIVE", 28.6519, 77.1909, 35, 35, 4, 12, (21, 10), "Elevated tank, 2 lakh litre."),
 ("Saket Water Tank", "WATER_TANK", "NEEDS_REPAIR", 28.5245, 77.2066, 42, 38, 4, -6, (10, 10), "Valve leakage reported."),
 ("Dwarka Sec-10 Water Tank", "WATER_TANK", "ACTIVE", 28.5823, 77.0500, 48, 40, 5, 30, (5, 10), "Ground-level sump and pump house."),
 ("Rohini Sec-7 Water Tank", "WATER_TANK", "ACTIVE", 28.7041, 77.1025, 40, 40, 4, 0, None, "Newly commissioned, not yet inspected."),
 ("Vasant Kunj Water Tank", "WATER_TANK", "CLOSED", 28.5200, 77.1580, 36, 30, 4, 18, (2, 10), "Decommissioned; awaiting demolition."),
 ("Solar Block C", "SOLAR_SITE", "CLOSED", 28.5355, 77.2750, 62, 55, 4, 10, (2, 10), "Inverter replacement in progress."),
 ("Rithala Solar Array", "SOLAR_SITE", "ACTIVE", 28.7230, 77.1060, 140, 90, 4, -4, (19, 10), "1.2 MW ground-mounted array."),
 ("Bawana Solar Park", "SOLAR_SITE", "ACTIVE", 28.7950, 77.0450, 220, 160, 5, 7, (14, 10), "Perimeter fence and panel wash cycle."),
 ("Mundka Depot Rooftop Solar", "SOLAR_SITE", "NEEDS_REPAIR", 28.6830, 77.0270, 120, 60, 4, 0, (8, 10), "Two strings offline."),
 ("Najafgarh Pumping Station", "SUBSTATION", "ACTIVE", 28.6090, 76.9790, 80, 60, 5, 22, (11, 10), "Pump house and 11 kV panel."),
 ("Okhla Substation", "SUBSTATION", "ACTIVE", 28.5470, 77.2720, 90, 70, 4, -10, (6, 10), "33/11 kV substation."),
 ("Pragati Maidan Store Yard", "BUILDING", "ACTIVE", 28.6180, 77.2440, 110, 75, 5, 14, (3, 10), "Maintenance store and yard."),
 ("Karol Bagh Community Hall", "BUILDING", "NEEDS_REPAIR", 28.6440, 77.1900, 45, 30, 4, 25, (16, 10), "Roof waterproofing needed."),
 ("Yamuna Biodiversity Plot", "OTHER", "ACTIVE", 28.7100, 77.2300, 260, 180, 7, 3, (27, 9), "Restored floodplain wetland."),
 ("Dilli Haat Parking Block", "OTHER", "ACTIVE", 28.5740, 77.2080, 130, 85, 5, -16, None, "Surface parking and EV chargers."),
]

sites = []
for i, (name, cat, status, lat, lng, w, h, n, rot, last, desc) in enumerate(SITES, 1):
    pts = shape(lat, lng, w, h, n, rot, seed=i * 17)
    a = area_sqm(pts)
    sites.append({
        "id": f"site-{i:03d}", "name": name, "category": cat, "status": status,
        "description": desc, "boundary": pts,
        "lastInspectionAt": ms(2026, last[1], last[0]) if last else None,
        "version": 1, "fieldVersions": {}, "fieldEditedAt": {},
    })
    print(f"{name:34s} {cat:11s} {a:10.0f} m2")

users = [{"email": "inspector@fieldpin.app", "name": "Asha Verma",
          "passwordSha256": hashlib.sha256(b"Field@123").hexdigest()}]
out = "app/src/main/assets/mock_server_seed.json"
with open(out, "w") as f:
    json.dump({"users": users, "sites": sites}, f, separators=(",", ":"))
print("wrote", out, len(sites), "sites")
