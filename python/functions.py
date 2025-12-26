import time
from typing import Tuple, Dict, Optional, Any, List, Set
import psycopg2
import requests
import json
import h3
import folium

# Helper to build overpass query.
def build_overpass_query(bbox: Tuple[float, float, float, float],
                         overpass_filters: Dict[str, str]
                         ) -> str:
    bbox_str = f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]}"

    query = f"""
        [out:json][timeout:10000];
        (
            nwr["tourism"~"{overpass_filters.get('tourism', '')}"]({bbox_str});
            nwr["historic"]({bbox_str});
            nwr["amenity"~"{overpass_filters.get('amenity', '')}"]({bbox_str});
            nwr["leisure"~"{overpass_filters.get('leisure', '')}"]({bbox_str});
            node["man_made"~"{overpass_filters.get('man_made', '')}"]({bbox_str});
            way["man_made"~"{overpass_filters.get('man_made', '')}"]({bbox_str});
            node["natural"~"{overpass_filters.get('natural', '')}"]({bbox_str});
            node["waterway"="{overpass_filters.get('waterway', '')}"]({bbox_str});
            node["craft"~"{overpass_filters.get('craft', '')}"]({bbox_str});
            way["craft"~"{overpass_filters.get('craft', '')}"]({bbox_str});
        );
        out center;
    """
    return query

# Helper to extract lat/lon from different parts of the Overpass response.
def _extract_location(element: Dict[str, Any]) -> Optional[Tuple[float, float]]:
    if "lat" in element and "lon" in element:
        return element["lat"], element["lon"]

    if "center" in element:
        lat = element["center"].get("lat")
        lon = element["center"].get("lon")
        if lat is not None and lon is not None:
            return lat, lon

    return None

# Parses a raw Overpass element into our internal POI structure.
def _parse_poi_element(element: Dict[str, Any],
                       poi_keys: Dict[str, int],
                       city: str
                       ) -> Optional[Dict[str, Any]]:
    tags = element.get("tags", {})
    if not tags:
        return None

    poi_type = next((k for k in poi_keys.keys() if k in tags), None)
    if not poi_type:
        return None

    poi = {
        "id": f"{element['type']}/{element['id']}",
        "city": city,
        "name": tags.get("name", tags.get("name:en")),
        "poi_type": poi_type,
        "poi_subtype": tags.get(poi_type),
        "location": _extract_location(element)
    }

    if "geometry" in element:
        poi["boundary"] = [(p.get("lat"), p.get("lon")) for p in element["geometry"]]

    return poi

# Generates H3 cells covering a rectangular bounding box.
def _hexagons_from_bbox(bbox: Tuple[float, float, float, float],
                        resolution: int
                        ) -> Set[str]:
    south, west, north, east = bbox

    box_coords = [
        (south, west),
        (south, east),
        (north, east),
        (north, west),
        (south, west)
    ]

    return _hexagons_from_coords(box_coords, resolution)

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

# Converts a list of (lat, lon) coordinates forming a polygon into H3 cells.
def _hexagons_from_coords(coords: List[Tuple[float, float]],
                          resolution: int
                          ) -> Set[str]:
    polygon_h3 = h3.LatLngPoly(coords)
    return h3.polygon_to_cells(polygon_h3, resolution)

def fetch_pois(bbox: Tuple[float, float, float, float],
               poi_keys: Dict[str, int],
               overpass_filters: Dict[str, str],
               city: str,
               max_retries: int = 3,
               initial_delay: int = 5
               ) -> List[Dict[str, Any]]:
    query = build_overpass_query(bbox, overpass_filters)
    url = "https://overpass-api.de/api/interpreter"

    current_delay = initial_delay

    for attempt in range(1, max_retries + 1):
        try:
            resp = requests.post(url, data={"data": query}, timeout=10000)
            resp.raise_for_status()
            data = resp.json()

            elements = data.get("elements", [])
            pois = []

            for element in elements:
                poi = _parse_poi_element(element, poi_keys, city)
                if poi:
                    pois.append(poi)

            return pois
        except requests.RequestException as e:
            if attempt < max_retries:
                print(f"Error fetching POIs: {e}. Waiting {current_delay}s before retry...")
                time.sleep(current_delay)
                current_delay *= 2
            else:
                print(f"Failed to fetch POIs for {city} after {max_retries} attempts.")
                return []

    return []

# Maps POIs to H3 hexagons based on location (point) or boundary (polygon)
def assign_pois_to_hexagons(hexagons: Set[str],
                            pois: List[Dict[str, Any]],
                            resolution: int
                            ) -> List[Dict[str, Any]]:
    hex_poi_map = {hex_id: [] for hex_id in hexagons}
    valid_hex_set = set(hexagons)

    for poi in pois:
        if poi.get("location"):
            poi_lat, poi_lon = poi["location"]
            poi_hex = h3.latlng_to_cell(poi_lat, poi_lon, resolution)

            if poi_hex in valid_hex_set:
                poi_entry = poi.copy()
                poi_entry["id"] = f"{poi['id']}#{poi_hex}"
                hex_poi_map[poi_hex].append(poi_entry)

        elif poi.get("boundary"):
            poi_hexagons = _hexagons_from_coords(poi["boundary"], resolution)

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
def calculate_weights(hexagons: List[Dict[str, Any]],
                      poi_factor: float,
                      poi_keys: Dict[str, int]
                      ) -> List[Dict[str, Any]]:
    uniform_factor = 1 - poi_factor

    for hexagon in hexagons:
        raw_weight = 0
        for poi in hexagon["pois"]:
            raw_weight += poi_keys.get(poi["poi_type"], 0)
        hexagon["weight"] = raw_weight

    weight_sum = sum(h["weight"] for h in hexagons) or 1

    hex_count = len(hexagons)
    uniform_weight = 1.0 / hex_count if hex_count > 0 else 0

    for hexagon in hexagons:
        poi_share = hexagon["weight"] / weight_sum
        hexagon["weight"] = (poi_share * poi_factor) + (uniform_weight * uniform_factor)

    return hexagons

# Persists City, Hexagons, and POIs data into PostgreSQL database
def save_to_db(hexagons: List[Dict[str, Any]],
               bbox: Tuple[float, float, float, float],
               country: str,
               city: str,
               connection_string: str
               ) -> None:
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
        INSERT INTO "Pois" ("Id", "Name", "PoiType", "PoiSubtype", "Location", "Boundary", "IsPromoted", "HexagonId", "CityId")
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT ("Id") DO UPDATE SET
            "Name" = EXCLUDED."Name",
            "PoiType" = EXCLUDED."PoiType",
            "PoiSubtype" = EXCLUDED."PoiSubtype",
            "Location" = EXCLUDED."Location",
            "Boundary" = EXCLUDED."Boundary",
            "IsPromoted" = EXCLUDED."IsPromoted",
            "HexagonId" = EXCLUDED."HexagonId",
            "CityId" = EXCLUDED."CityId";
    """

    with psycopg2.connect(connection_string) as conn:
        with conn.cursor() as cursor:

            cursor.execute(upsert_city, (city, country, json.dumps(bbox)))

            for hexagon in hexagons:
                cursor.execute(upsert_hexagon, (
                    hexagon["id"],
                    json.dumps(hexagon["boundaries"]),
                    json.dumps(hexagon["center"]),
                    city,
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
                        False,
                        hexagon["id"],
                        city
                    ))

        conn.commit()

# Generates an interactive HTML map visualizing the hexagons and their weights
def visualize_hexagons(hexagons: List[Dict[str, Any]],
                       output_filename: str
                       ) -> None:
    m = folium.Map(zoom_start=12, tiles="cartodbpositron")

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

    m.save(f"{output_filename}.html")