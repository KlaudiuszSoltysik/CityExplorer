import json
from typing import Any, Dict
from pyproj import Transformer

# Configuration constants
SRC_CRS = "EPSG:2180"
DST_CRS = "EPSG:4326"

# Recursive function to handle nested GeoJSON coordinates
def transform_coordinates(
        coords: Any,
        geometry_type: str,
        transformer: Transformer
) -> Any:
    if geometry_type == "Point":
        return list(transformer.transform(coords[0], coords[1]))

    elif geometry_type in ("LineString", "MultiPoint"):
        return [
            list(transformer.transform(x, y)) for x, y in coords
        ]

    elif geometry_type in ("Polygon", "MultiLineString"):
        return [
            transform_coordinates(ring, "LineString", transformer)
            for ring in coords
        ]

    elif geometry_type == "MultiPolygon":
        return [
            transform_coordinates(poly, "Polygon", transformer)
            for poly in coords
        ]

    return coords

# Core logic for processing GeoJSON features
def process_geojson_data(geojson_data: Dict[str, Any], transformer: Transformer) -> None:
    features = geojson_data.get("features", [])

    for feature in features:
        geometry = feature.get("geometry")
        if geometry and "coordinates" in geometry:
            geom_type = geometry["type"]
            geometry["coordinates"] = transform_coordinates(
                geometry["coordinates"],
                geom_type,
                transformer
            )

# File I/O handler
def convert_geojson_to_wgs84(filename: str) -> None:
    transformer = Transformer.from_crs(SRC_CRS, DST_CRS, always_xy=True)

    try:
        with open(filename, "r", encoding="utf-8") as f:
            geojson_data = json.load(f)

        process_geojson_data(geojson_data, transformer)

        with open(filename, "w", encoding="utf-8") as f:
            json.dump(geojson_data, f, indent=2)

    except Exception as e:
        print(f"Error processing file {filename}: {e}")

if __name__ == "__main__":
    convert_geojson_to_wgs84("raw_data/poznan.geojson")