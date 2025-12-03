from typing import Tuple, Dict, Optional, Any, List, Set
import psycopg2
import requests
import json
import h3
import folium

# Configuration
INPUT_FILENAME = "geojsons/gdansk.geojson"
OUTPUT_FILENAME = "hexagons.json"
CITY = "Gdańsk"
COUNTRY = "Poland"
RESOLUTION = 9
POI_FACTOR = 0.5
CONNECTION_STRING = "host=localhost port=6000 user=admin password=admin dbname=postgres"

POI_KEYS = {
    "tourism": 4,
    "historic": 4,
    "amenity": 2,
    "leisure": 1,
    "natural": 3,
    "waterway": 3,
    "craft": 2
}

OVERPASS_FILTERS = {
    "tourism": "attraction|artwork|gallery|museum|viewpoint|zoo|aquarium|theme_park",
    "amenity": "place_of_worship|arts_centre|theatre|planetarium|fountain|townhall|restaurant|cafe|bar|pub|ice_cream|cinema|nightclub|community_centre|library|public_bookcase|marketplace",
    "leisure": "park|nature_reserve|water_park|sports_centre|fitness_centre|swimming_pool|pitch|ice_rink|bowling_alley",
    "man_made": "lighthouse|windmill|watermill|tower|obelisk",
    "natural": "cave_entrance|beach",
    "waterway": "waterfall",
    "craft": "brewery|winery"
}

def build_overpass_query(bbox: Tuple[float, float, float, float]) -> str:
    bbox_str = f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}"

    query = f"""
        [out:json][timeout:100];
        (
            nwr["tourism"~"{OVERPASS_FILTERS['tourism']}"]({bbox_str});
            
            nwr["historic"]({bbox_str});
            
            nwr["amenity"~"{OVERPASS_FILTERS['amenity']}"]({bbox_str});
            
            nwr["leisure"~"{OVERPASS_FILTERS['leisure']}"]({bbox_str});
            
            node["man_made"~"{OVERPASS_FILTERS['man_made']}"]({bbox_str});
            way["man_made"~"{OVERPASS_FILTERS['man_made']}"]({bbox_str});
            
            node["natural"~"{OVERPASS_FILTERS['natural']}"]({bbox_str});
            
            node["waterway"="{OVERPASS_FILTERS['waterway']}"]({bbox_str});
            
            node["craft"~"{OVERPASS_FILTERS['craft']}"]({bbox_str});
            way["craft"~"{OVERPASS_FILTERS['craft']}"]({bbox_str});
        );
        out center;
    """
    return query

# Helper to extract lat/lon from different parts of the Overpass response.
def _extract_location(element: Dict[str, Any]) -> Optional[Tuple[float, float]]:

    if "lat" in element and "lon" in element:
        return element["lat"], element["lon"]

    if "center" in element:
        return element["center"].get("lat"), element["center"].get("lon")

    return None

# Parses a raw Overpass element into our internal POI structure.
def _parse_poi_element(element: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    tags = element.get("tags", {})
    if not tags:
        return None

    poi_type = next((k for k in POI_KEYS.keys() if k in tags), None)
    if not poi_type:
        return None

    poi = {
        "id": f"{element['type']}/{element['id']}",
        "city": CITY,
        "name": tags.get("name", tags.get("name:en")),
        "poi_type": poi_type,
        "poi_subtype": tags.get(poi_type),
        "location": _extract_location(element)
    }

    if "geometry" in element:
        poi["boundary"] = [(p.get("lat"), p.get("lon")) for p in element["geometry"]]

    return poi

def fetch_pois(bbox: Tuple[float, float, float, float]) -> List[Dict[str, Any]]:
    query = build_overpass_query(bbox)
    url = "https://overpass-api.de/api/interpreter"

    try:
        resp = requests.post(url, data={"data": query}, timeout=500)
        resp.raise_for_status()
        data = resp.json()
    except requests.RequestException as e:
        print(f"Error fetching POIs: {e}")
        return []

    elements = data.get("elements", [])
    pois = []

    for element in elements:
        poi = _parse_poi_element(element)
        if poi:
            pois.append(poi)

    return pois

# Converts a list of (lat, lon) coordinates forming a polygon into H3 cells.
def _hexagons_from_coords(coords: List[Tuple[float, float]]) -> Set[str]:
    polygon_h3 = h3.LatLngPoly(coords)
    return h3.polygon_to_cells(polygon_h3, RESOLUTION)

# Generates H3 cells covering a rectangular bounding box.
def _hexagons_from_bbox(bbox: Tuple[float, float, float, float]) -> Set[str]:
    south, west, north, east = bbox

    box_coords = [
        (south, west),
        (south, east),
        (north, east),
        (north, west),
        (south, west)
    ]

    return _hexagons_from_coords(box_coords)

# Maps POIs to H3 hexagons based on location (point) or boundary (polygon)
def assign_pois_to_hexagons(hexagons: Set[str], pois: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    hex_poi_map = {hex_id: [] for hex_id in hexagons}
    valid_hex_set = set(hexagons)

    for poi in pois:
        if poi.get("location"):
            poi_lat, poi_lon = poi["location"]
            poi_hex = h3.latlng_to_cell(poi_lat, poi_lon, RESOLUTION)

            if poi_hex in valid_hex_set:
                poi_entry = poi.copy()
                poi_entry["id"] = f"{poi['id']}#{poi_hex}"
                hex_poi_map[poi_hex].append(poi_entry)

        elif poi.get("boundary"):
            poi_hexagons = _hexagons_from_coords(poi["boundary"])

            for hex_id in poi_hexagons:
                if hex_id in valid_hex_set:
                    poi_entry = poi.copy()
                    poi_entry["id"] = f"{poi['id']}#{hex_id}"
                    hex_poi_map[hex_id].append(poi_entry)

    return [
        {"id": hex_id, "pois": hex_poi_map[hex_id]}
        for hex_id in hexagons
    ]

# Enriches hexagon objects with their geographic boundary coordinates and center point
def attach_boundaries(hexagons_data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    for item in hexagons_data:
        h3_address = item["id"]

        boundary_coords = list(h3.cell_to_boundary(h3_address))
        if boundary_coords:
            boundary_coords.append(boundary_coords[0])

        item["boundaries"] = boundary_coords
        item["center"] = h3.cell_to_latlng(h3_address)

    return hexagons_data

# Calculates weighted score for each hexagon based on POI density and uniform distribution
def calculate_weights(hexagons: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    uniform_factor = 1 - POI_FACTOR

    for hexagon in hexagons:
        raw_weight = 0
        for poi in hexagon["pois"]:
            raw_weight += POI_KEYS.get(poi["poi_type"], 0)
        hexagon["weight"] = raw_weight

    weight_sum = sum(h["weight"] for h in hexagons) or 1

    hex_count = len(hexagons)
    uniform_weight = 1.0 / hex_count if hex_count > 0 else 0

    for hexagon in hexagons:
        poi_share = hexagon["weight"] / weight_sum
        hexagon["weight"] = (poi_share * POI_FACTOR) + (uniform_weight * uniform_factor)

    return hexagons

# Persists City, Hexagons, and POIs data into PostgreSQL database
def save_to_db(hexagons: List[Dict[str, Any]], bbox: Tuple[float, float, float, float]) -> None:
    upsert_city = """
        INSERT INTO "Cities" ("City", "Country", "Bbox")
        VALUES (%s, %s, %s)
        ON CONFLICT ("City") DO UPDATE SET
            "Country" = EXCLUDED."Country",
            "Bbox" = EXCLUDED."Bbox";
    """

    upsert_hexagon = """
        INSERT INTO "Hexagons" ("Id", "Boundaries", "Center", "CityId", "Weight")
        VALUES (%s, %s, %s, %s, %s)
        ON CONFLICT ("Id") DO UPDATE SET
            "Boundaries" = EXCLUDED."Boundaries",
            "Center" = EXCLUDED."Center",
            "CityId" = EXCLUDED."CityId",
            "Weight" = EXCLUDED."Weight";
    """

    upsert_poi = """
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
    """

    with psycopg2.connect(CONNECTION_STRING) as conn:
        with conn.cursor() as cursor:

            cursor.execute(upsert_city, (CITY, COUNTRY, json.dumps(bbox)))

            for hexagon in hexagons:
                cursor.execute(upsert_hexagon, (
                    hexagon["id"],
                    json.dumps(hexagon["boundaries"]),
                    json.dumps(hexagon["center"]),
                    CITY,
                    hexagon["weight"]
                ))

                for poi in hexagon["pois"]:
                    cursor.execute(upsert_poi, (
                        poi["id"],
                        poi["name"],
                        poi["poi_type"],
                        poi["poi_subtype"],
                        json.dumps(poi.get("location")),
                        json.dumps(poi.get("boundary")),
                        hexagon["id"],
                        CITY
                    ))

        conn.commit()

# Generates an interactive HTML map visualizing the hexagons and their weights
def visualize_hexagons(hexagons: List[Dict[str, Any]]) -> None:
    m = folium.Map(zoom_start=12, tiles="cartodbpositron")

    if not hexagons:
        print("No hexagons to visualize.")
        return

    max_weight = max(h["weight"] for h in hexagons) or 1

    for hexagon in hexagons:
        weight = hexagon["weight"]

        fill_opacity = float(weight / max_weight)
        color = "#3186cc"
        boundaries = hexagon["boundaries"]

        folium.Polygon(
            locations=boundaries,
            fill=True,
            fill_color=color,
            fill_opacity=fill_opacity,
            color=color,
            weight=0.5,
            tooltip=f"Hex ID: {hexagon['id']}<br>Weight: {weight:.4f}"
        ).add_to(m)

    m.save("hexagons.html")

# Helper function to parse input geometry
def load_region_data(filename):
    with open(filename, "r", encoding="utf-8-sig") as f:
        geojson_data = json.load(f)

    geojson_coords = geojson_data["features"][0]["geometry"]["coordinates"][0][0]

    coords_h3 = [(point[1], point[0]) for point in geojson_coords]

    lats = [p[0] for p in coords_h3]
    lons = [p[1] for p in coords_h3]
    bbox = (min(lats), min(lons), max(lats), max(lons))

    return coords_h3, bbox

def main():
    coords_h3, bbox = load_region_data(INPUT_FILENAME)

    hexagons = _hexagons_from_coords(coords_h3)
    pois = fetch_pois(bbox)

    hexagons = assign_pois_to_hexagons(hexagons, pois)
    hexagons = attach_boundaries(hexagons)
    hexagons = calculate_weights(hexagons)

    save_to_db(hexagons, bbox)
    visualize_hexagons(hexagons)

    with open(OUTPUT_FILENAME, "w", encoding="utf-8") as f:
        json.dump(hexagons, f, indent=2)

if __name__ == "__main__":
    main()
