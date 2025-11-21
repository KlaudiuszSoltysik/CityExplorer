import json
from pyproj import Transformer

def transform_coordinates(coords, geometry_type, transformer):
    if geometry_type == "Point":
        return list(transformer.transform(coords[0], coords[1]))

    elif geometry_type in ["LineString", "MultiPoint"]:
        new_coords = []
        for x, y in coords:
            new_coords.append(list(transformer.transform(x, y)))
        return new_coords

    elif geometry_type in ["Polygon", "MultiLineString"]:
        return [transform_coordinates(ring_or_line, "LineString", transformer) for ring_or_line in coords]

    elif geometry_type == "MultiPolygon":
        return [transform_coordinates(polygon, "Polygon", transformer) for polygon in coords]

    return coords

def convert_geojson_to_wgs84(filename):
    crs_from = "EPSG:2180"
    crs_to = "EPSG:4326"

    transformer = Transformer.from_crs(crs_from, crs_to, always_xy=True)

    try:
        with open(filename, 'r', encoding='utf-8') as f:
            geojson_data = json.load(f)

        for feature in geojson_data.get("features", []):
            geometry = feature.get("geometry")
            if geometry and geometry.get("coordinates"):
                geom_type = geometry["type"]
                geometry["coordinates"] = transform_coordinates(geometry["coordinates"], geom_type, transformer)

        output_filename = filename
        with open(output_filename, 'w', encoding='utf-8') as f:
            json.dump(geojson_data, f, indent=2)
    except Exception as e:
        print(f"Error: {e}")

convert_geojson_to_wgs84("geojsons/poznan.geojson")