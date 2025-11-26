import psycopg2
import requests
import json
import h3
import folium


def build_overpass_query(bbox, receiver):
    bbox_str = f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}"
    query = ""

    if receiver == "tourist":
        query = f"""
            [out:json][timeout:100];
            (
            node["tourism"~"attraction|artwork|gallery|museum|viewpoint|zoo|aquarium|theme_park"]({bbox_str});
            way["tourism"~"attraction|artwork|gallery|museum|viewpoint|zoo|aquarium|theme_park"]({bbox_str});
            relation["tourism"~"attraction|artwork|gallery|museum|viewpoint|zoo|aquarium|theme_park"]({bbox_str});
            node["historic"]({bbox_str});
            way["historic"]({bbox_str});
            relation["historic"]({bbox_str});
            node["amenity"~"place_of_worship|arts_centre|theatre|planetarium|fountain|townhall"]({bbox_str});
            way["amenity"~"place_of_worship|arts_centre|theatre|planetarium|fountain|townhall"]({bbox_str});
            relation["amenity"~"place_of_worship|arts_centre|theatre|planetarium|fountain|townhall"]({bbox_str});
            node["leisure"~"park|nature_reserve|water_park"]({bbox_str});
            way["leisure"~"park|nature_reserve|water_park"]({bbox_str});
            relation["leisure"~"park|nature_reserve|water_park"]({bbox_str});
            node["man_made"~"lighthouse|windmill|watermill|tower|obelisk"]({bbox_str});
            way["man_made"~"lighthouse|windmill|watermill|tower|obelisk"]({bbox_str});
            node["natural"~"cave_entrance|beach"]({bbox_str});
            node["waterway"="waterfall"]({bbox_str});
            );
            out center;
        """
    elif receiver == "local":
        query = f"""
            [out:json][timeout:100];
            (
            node["amenity"~"restaurant|cafe|bar|pub|ice_cream"]({bbox_str});
            way["amenity"~"restaurant|cafe|bar|pub|ice_cream"]({bbox_str});
            relation["amenity"~"restaurant|cafe|bar|pub|ice_cream"]({bbox_str});
            node["amenity"~"cinema|nightclub|community_centre|library|public_bookcase|marketplace"]({bbox_str});
            way["amenity"~"cinema|nightclub|community_centre|library|public_bookcase|marketplace"]({bbox_str});
            relation["amenity"~"cinema|nightclub|community_centre|library|public_bookcase|marketplace"]({bbox_str});
            node["leisure"~"sports_centre|fitness_centre|swimming_pool|pitch|ice_rink|bowling_alley"]({bbox_str});
            way["leisure"~"sports_centre|fitness_centre|swimming_pool|pitch|ice_rink|bowling_alley"]({bbox_str});
            relation["leisure"~"sports_centre|fitness_centre|swimming_pool|pitch|ice_rink|bowling_alley"]({bbox_str});
            node["craft"~"brewery|winery"]({bbox_str});
            way["craft"~"brewery|winery"]({bbox_str});
            );
            out center;
        """

    return query

def fetch_pois(bbox, receiver):
    q = build_overpass_query(bbox, receiver)
    resp = requests.post("https://overpass-api.de/api/interpreter", data={"data": q}, timeout=200)
    resp.raise_for_status()
    data = resp.json()
    elements = data.get("elements", [])

    pois = []
    for el in elements:
        tags = el.get("tags", {}) or {}

        poi_type = None
        poi_keys = TOURIST_POI_KEYS.keys() if receiver == "tourist" else LOCAL_POI_KEYS.keys()

        for k in poi_keys:
            if k in tags:
                poi_type = k
                break

        if not poi_type:
            continue

        poi = {
            "id": f"{el["type"]}/{el["id"]}",
            "name": tags.get("name", tags.get("name:en", None)),
            "poi_type": poi_type,
            "poi_subtype": tags.get(poi_type) if poi_type else None,
        }

        if "lat" in el:
            poi["location"] = (el["lat"], el["lon"])
        elif "center" in el:
            poi["location"] = (el["center"].get("lat"), el["center"].get("lon"))
        if "geometry" in el:
            poi["boundary"] = [(p.get("lat"), p.get("lon")) for p in el["geometry"]]

        pois.append(poi)

    return pois

def hexagons_from_coords(coords_h3):
    polygon_h3 = h3.LatLngPoly(coords_h3)
    return h3.polygon_to_cells(polygon_h3, RESOLUTION)

def hexagons_from_bbox(bbox):
    south, west, north, east = bbox

    coords_h3 = [
        (south, west),
        (south, east),
        (north, east),
        (north, west),
        (south, west)
    ]

    polygon_h3 = h3.LatLngPoly(coords_h3)
    return h3.polygon_to_cells(polygon_h3, RESOLUTION)

def assign_pois_to_hexagons(hexagons, tourist_pois, local_pois):
    hexagons_with_pois = []

    hex_tourist_poi_map = {hex: [] for hex in hexagons}
    hex_local_poi_map = {hex: [] for hex in hexagons}

    valid_hex_set = set(hexagons)

    for poi in tourist_pois:
        if poi.get("location"):
            poi_lat = poi["location"][0]
            poi_lon = poi["location"][1]

            poi_hex = h3.latlng_to_cell(poi_lat, poi_lon, RESOLUTION)

            if poi_hex in valid_hex_set:
                poi["id"] += "#" + poi_hex
                hex_tourist_poi_map[poi_hex].append(poi)
        else:
            poi_hexagons = hexagons_from_bbox(poi["boundary"])

            for hex_id in poi_hexagons:
                if hex_id in valid_hex_set:
                    new_poi = dict(poi)
                    new_poi["id"] += "#" + hex_id
                    hex_tourist_poi_map[hex_id].append(new_poi)

    for poi in local_pois:
        if poi.get("location"):
            poi_lat = poi["location"][0]
            poi_lon = poi["location"][1]

            poi_hex = h3.latlng_to_cell(poi_lat, poi_lon, RESOLUTION)

            if poi_hex in valid_hex_set:
                poi["id"] += "#" + poi_hex
                hex_local_poi_map[poi_hex].append(poi)
        else:
            poi_hexagons = hexagons_from_bbox(poi["boundary"])

            for hex_id in poi_hexagons:
                if hex_id in valid_hex_set:
                    new_poi = dict(poi)
                    new_poi["id"] += "#" + hex_id
                    hex_local_poi_map[hex_id].append(new_poi)

    for hex in hexagons:
        hexagons_with_pois.append({
            "id": hex,
            "tourist_pois": hex_tourist_poi_map[hex],
            "local_pois": hex_local_poi_map[hex]
        })

    return hexagons_with_pois

def attach_boundaries(hexagons):
    for hex in hexagons:
        hex["boundaries"] = h3.cell_to_boundary(hex["id"])

    return hexagons

def calculate_weights(hexagons):
    for hex in hexagons:
        hex["tourist_weight"] = 0
        hex["local_weight"] = 0

        for poi in hex["tourist_pois"]:
            hex["tourist_weight"] += TOURIST_POI_KEYS.get(poi["poi_type"], 0)

        for poi in hex["tourist_pois"] + hex["local_pois"]:
            hex["local_weight"] += {**TOURIST_POI_KEYS, **LOCAL_POI_KEYS}.get(poi["poi_type"], 0)

    max_tourist_weight = max(hex["tourist_weight"] for hex in hexagons) or 1
    max_local_weight = max(hex["local_weight"] for hex in hexagons) or 1
    for hex in hexagons:
        hex["tourist_weight"] = hex["tourist_weight"] / max_tourist_weight * 1000
        hex["local_weight"] = hex["local_weight"] / max_local_weight * 1000

    tourist_weight_sum = sum(hex["tourist_weight"] for hex in hexagons)
    local_weight_sum = sum(hex["local_weight"] for hex in hexagons)
    for hex in hexagons:
        hex["tourist_weight"] = hex["tourist_weight"] / tourist_weight_sum
        hex["local_weight"] = hex["local_weight"] / local_weight_sum

    return hexagons

def save_to_db(hexagons):
    conn = psycopg2.connect(CONNECTION_STRING)

    cursor = conn.cursor()

    for hex in hexagons:
        cursor.execute("""
            INSERT INTO "Hexagons" ("Id", "Boundaries", "Country", "City", "TouristWeight", "LocalWeight")
            VALUES (%s, %s, %s, %s, %s, %s)
            ON CONFLICT ("Id") DO UPDATE SET
                "Boundaries" = EXCLUDED."Boundaries",
                "Country" = EXCLUDED."Country",
                "City" = EXCLUDED."City",
                "TouristWeight" = EXCLUDED."TouristWeight",
                "LocalWeight" = EXCLUDED."LocalWeight";
        """, (hex["id"], json.dumps(hex["boundaries"]), COUNTRY, CITY, hex["tourist_weight"], hex["local_weight"]))

        for poi in hex.get("tourist_pois", []):
            cursor.execute("""
                INSERT INTO "Pois" ("Id", "Name", "PoiType", "PoiSubtype", "Location", "Boundary", "TouristHexagonId")
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT ("Id") DO UPDATE SET
                    "Name" = EXCLUDED."Name",
                    "PoiType" = EXCLUDED."PoiType",
                    "PoiSubtype" = EXCLUDED."PoiSubtype",
                    "Location" = EXCLUDED."Location",
                    "Boundary" = EXCLUDED."Boundary",
                    "TouristHexagonId" = EXCLUDED."TouristHexagonId";
            """, (poi["id"], poi["name"], poi["poi_type"], poi["poi_subtype"], json.dumps(poi.get("location")), json.dumps(poi.get("boundary")), hex["id"]))

        for poi in hex.get("local_pois", []):
            cursor.execute("""
                INSERT INTO "Pois" ("Id", "Name", "PoiType", "PoiSubtype", "Location", "Boundary", "LocalHexagonId")
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT ("Id") DO UPDATE SET
                    "Name" = EXCLUDED."Name",
                    "PoiType" = EXCLUDED."PoiType",
                    "PoiSubtype" = EXCLUDED."PoiSubtype",
                    "Location" = EXCLUDED."Location",
                    "Boundary" = EXCLUDED."Boundary",
                    "LocalHexagonId" = EXCLUDED."LocalHexagonId";
            """, (poi["id"], poi["name"], poi["poi_type"], poi["poi_subtype"], json.dumps(poi.get("location")), json.dumps(poi.get("boundary")), hex["id"]))

    conn.commit()
    cursor.close()
    conn.close()

def visualize_hexagons(hexagons):
    m = folium.Map(zoom_start=12, tiles="cartodbpositron")

    max_weight = max([h["tourist_weight"] for h in hexagons])

    for hex in hexagons:
        tourist_weight = hex["tourist_weight"]

        fill_opacity = float(tourist_weight/max_weight)

        color = "#3186cc"

        boundaries = hex["boundaries"]

        folium.Polygon(
            locations=boundaries,
            fill=True,
            fill_color=color,
            fill_opacity=fill_opacity,
            color=color,
            weight=0.5,
            tooltip=f"Hex ID: {hex["id"]}<br>Tourist Weight: {tourist_weight:.4f}"
        ).add_to(m)

    m.save("hexagons.html")


INPUT_FILENAME = "geojsons/berlin.geojson"
CITY = "Berlin"
COUNTRY = "Germany"
TOURIST_POI_KEYS = {"tourism": 4, "historic": 4, "amenity": 2, "leisure": 2, "natural": 2, "waterway": 2}
LOCAL_POI_KEYS = {"amenity": 1, "leisure": 1, "craft": 1}
RESOLUTION = 9
CONNECTION_STRING = "host=localhost port=6000 user=admin password=admin dbname=postgres"


with open(INPUT_FILENAME, "r", encoding="utf-8-sig") as f:
    geojson_data = json.load(f)
geojson_coords = geojson_data["features"][0]["geometry"]["coordinates"][0][0]
coords_h3 = [(point[1], point[0]) for point in geojson_coords]
lats = [p[0] for p in coords_h3]
lons = [p[1] for p in coords_h3]
bbox = (min(lats), min(lons), max(lats), max(lons))
hexagons = hexagons_from_coords(coords_h3)
tourist_pois = fetch_pois(bbox, "tourist")
local_pois = fetch_pois(bbox, "local")
hexagons = assign_pois_to_hexagons(hexagons, tourist_pois, local_pois)
hexagons = attach_boundaries(hexagons)
hexagons = calculate_weights(hexagons)
save_to_db(hexagons)
visualize_hexagons(hexagons)
with open("hexagons.json", "w", encoding="utf-8") as f:
    json.dump(hexagons, f, indent=2)