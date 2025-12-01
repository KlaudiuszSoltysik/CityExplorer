import psycopg2
import requests
import json
import h3
import folium


def build_overpass_query(bbox):
    bbox_str = f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}"

    query = f"""
        [out:json][timeout:100];
        (
        node["tourism"~"attraction|artwork|gallery|museum|viewpoint|zoo|aquarium|theme_park"]({bbox_str});
        way["tourism"~"attraction|artwork|gallery|museum|viewpoint|zoo|aquarium|theme_park"]({bbox_str});
        relation["tourism"~"attraction|artwork|gallery|museum|viewpoint|zoo|aquarium|theme_park"]({bbox_str});

        node["historic"]({bbox_str});
        way["historic"]({bbox_str});
        relation["historic"]({bbox_str});

        node["amenity"~"place_of_worship|arts_centre|theatre|planetarium|fountain|townhall|restaurant|cafe|bar|pub|ice_cream|cinema|nightclub|community_centre|library|public_bookcase|marketplace"]({bbox_str});
        way["amenity"~"place_of_worship|arts_centre|theatre|planetarium|fountain|townhall|restaurant|cafe|bar|pub|ice_cream|cinema|nightclub|community_centre|library|public_bookcase|marketplace"]({bbox_str});
        relation["amenity"~"place_of_worship|arts_centre|theatre|planetarium|fountain|townhall|restaurant|cafe|bar|pub|ice_cream|cinema|nightclub|community_centre|library|public_bookcase|marketplace"]({bbox_str});

        node["leisure"~"park|nature_reserve|water_park|sports_centre|fitness_centre|swimming_pool|pitch|ice_rink|bowling_alley"]({bbox_str});
        way["leisure"~"park|nature_reserve|water_park|sports_centre|fitness_centre|swimming_pool|pitch|ice_rink|bowling_alley"]({bbox_str});
        relation["leisure"~"park|nature_reserve|water_park|sports_centre|fitness_centre|swimming_pool|pitch|ice_rink|bowling_alley"]({bbox_str});

        node["man_made"~"lighthouse|windmill|watermill|tower|obelisk"]({bbox_str});
        way["man_made"~"lighthouse|windmill|watermill|tower|obelisk"]({bbox_str});
        node["natural"~"cave_entrance|beach"]({bbox_str});
        node["waterway"="waterfall"]({bbox_str});

        node["craft"~"brewery|winery"]({bbox_str});
        way["craft"~"brewery|winery"]({bbox_str});
        );
        out center;
    """

    return query

def fetch_pois(bbox):
    q = build_overpass_query(bbox)
    resp = requests.post("https://overpass-api.de/api/interpreter", data={"data": q}, timeout=500)
    resp.raise_for_status()
    data = resp.json()
    elements = data.get("elements", [])

    pois = []
    for el in elements:
        tags = el.get("tags", {}) or {}

        poi_type = None
        poi_keys = POI_KEYS.keys()

        for k in poi_keys:
            if k in tags:
                poi_type = k
                break

        if not poi_type:
            continue

        poi = {
            "id": f"{el["type"]}/{el["id"]}",
            "city": CITY,
            "name": tags.get("name", tags.get("name:en", None)),
            "poi_type": poi_type,
            "poi_subtype": tags.get(poi_type) if poi_type else None
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

def assign_pois_to_hexagons(hexagons, pois):
    hexagons_with_pois = []

    hex_poi_map = {hex: [] for hex in hexagons}

    valid_hex_set = set(hexagons)

    for poi in pois:
        if poi.get("location"):
            poi_lat = poi["location"][0]
            poi_lon = poi["location"][1]

            poi_hex = h3.latlng_to_cell(poi_lat, poi_lon, RESOLUTION)

            if poi_hex in valid_hex_set:
                poi["id"] += "#" + poi_hex
                hex_poi_map[poi_hex].append(poi)
        else:
            poi_hexagons = hexagons_from_bbox(poi["boundary"])

            for hex_id in poi_hexagons:
                if hex_id in valid_hex_set:
                    new_poi = dict(poi)
                    new_poi["id"] += "#" + hex_id
                    hex_poi_map[hex_id].append(new_poi)

    for hex in hexagons:
        hexagons_with_pois.append({
            "id": hex,
            "pois": hex_poi_map[hex],
        })

    return hexagons_with_pois

def attach_boundaries(hexagons):
    for hex in hexagons:
        temp_list = list(h3.cell_to_boundary(hex["id"]))
        temp_list.append(temp_list[0])
        hex["boundaries"] = temp_list
        hex["center"] = h3.cell_to_latlng(hex["id"])

    return hexagons

def calculate_weights(hexagons):
    for hex in hexagons:
        hex["weight"] = 0

        for poi in hex["pois"]:
            hex["weight"] += POI_KEYS.get(poi["poi_type"], 0)

    weight_sum = sum(hex["weight"] for hex in hexagons) or 1

    hex_count = len(hexagons)
    uniform_weight = 1.0 / hex_count if hex_count > 0 else 0

    for hex in hexagons:
        poi_share = hex["weight"] / weight_sum
        hex["weight"] = (poi_share * 0.5) + (uniform_weight * 0.5)

    return hexagons

def save_to_db(hexagons, bbox):
    conn = psycopg2.connect(CONNECTION_STRING)

    cursor = conn.cursor()

    cursor.execute("""
        INSERT INTO "Cities" ("City", "Country", "Bbox")
        VALUES (%s, %s, %s)
        ON CONFLICT ("City") DO UPDATE SET
            "Country" = EXCLUDED."Country",
            "Bbox" = EXCLUDED."Bbox";
    """, (CITY, COUNTRY, json.dumps(bbox)))

    for hex in hexagons:
        cursor.execute("""
            INSERT INTO "Hexagons" ("Id", "Boundaries", "Center", "CityId", "Weight")
            VALUES (%s, %s, %s, %s, %s)
            ON CONFLICT ("Id") DO UPDATE SET
                "Boundaries" = EXCLUDED."Boundaries",
                "Center" = EXCLUDED."Center",
                "CityId" = EXCLUDED."CityId",
                "Weight" = EXCLUDED."Weight";
        """, (hex["id"], json.dumps(hex["boundaries"]), json.dumps(hex["center"]), CITY, hex["weight"]))

        for poi in hex["pois"]:
            cursor.execute("""
                INSERT INTO "Pois" ("Id", "Name", "PoiType", "PoiSubtype", "Location", "Boundary", "HexagonId", "CityId")
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT ("Id") DO UPDATE SET
                    "Name" = EXCLUDED."Name",
                    "PoiType" = EXCLUDED."PoiType",
                    "PoiSubtype" = EXCLUDED."PoiSubtype",
                    "Location" = EXCLUDED."Location",
                    "Boundary" = EXCLUDED."Boundary",
                    "HexagonId" = EXCLUDED."HexagonId",
                    "CityId" = EXCLUDED."CityId";
            """, (poi["id"], poi["name"], poi["poi_type"], poi["poi_subtype"], json.dumps(poi.get("location")), json.dumps(poi.get("boundary")), hex["id"], CITY))

    conn.commit()
    cursor.close()
    conn.close()

def visualize_hexagons(hexagons):
    m = folium.Map(zoom_start=12, tiles="cartodbpositron")

    max_weight = max([h["weight"] for h in hexagons])

    for hex in hexagons:
        weight = hex["weight"]

        fill_opacity = float(weight/max_weight)

        color = "#3186cc"

        boundaries = hex["boundaries"]

        folium.Polygon(
            locations=boundaries,
            fill=True,
            fill_color=color,
            fill_opacity=fill_opacity,
            color=color,
            weight=0.5,
            tooltip=f"Hex ID: {hex["id"]}<br>Weight: {weight:.4f}"
        ).add_to(m)

    m.save("hexagons.html")


INPUT_FILENAME = "geojsons/berlin.geojson"
CITY = "Berlin"
COUNTRY = "Germany"
POI_KEYS = {"tourism": 4, "historic": 4, "amenity": 2, "leisure": 1, "natural": 3, "waterway": 3, "craft": 2}
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
pois = fetch_pois(bbox)
hexagons = assign_pois_to_hexagons(hexagons, pois)
hexagons = attach_boundaries(hexagons)
hexagons = calculate_weights(hexagons)
save_to_db(hexagons, bbox)
visualize_hexagons(hexagons)
with open("hexagons.json", "w", encoding="utf-8") as f:
    json.dump(hexagons, f, indent=2)
