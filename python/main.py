import glob
import json
import os

from functions import load_region_data, _hexagons_from_coords, fetch_pois, assign_pois_to_hexagons, attach_boundaries, \
    calculate_weights, save_to_db, visualize_hexagons

# Configuration
CONNECTION_STRING = os.getenv("DB_CONNECTION_STRING", "host=localhost port=6000 user=admin password=admin dbname=postgres")
INPUT_DIRECTORY = os.getenv("INPUT_DIRECTORY", "./raw_data")
OUTPUT_DIRECTORY = os.getenv("OUTPUT_DIRECTORY", "./processed_data")

RESOLUTION = 9
POI_FACTOR = 0.5

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

def process_file(filepath: str) -> None:
    print(f"Processing {filepath}.")

    with open(filepath, 'r', encoding='utf-8-sig') as f:
        data = json.load(f)

    filename = os.path.basename(filepath)

    feature = data["features"][0]
    props = feature.get("properties", {})

    city = props.get("city")
    country = props.get("country")

    if not city or not country:
        print(f"Missing metadata in {filename}. Please ensure 'city' and 'country' are in properties.")
        return

    coords_h3, bbox = load_region_data(filepath)

    hexagons = _hexagons_from_coords(coords_h3, RESOLUTION)
    pois = fetch_pois(bbox, POI_KEYS, OVERPASS_FILTERS, city)

    hexagons = assign_pois_to_hexagons(hexagons, pois, RESOLUTION)
    hexagons = attach_boundaries(hexagons)
    hexagons = calculate_weights(hexagons, POI_FACTOR, POI_KEYS)

    save_to_db(hexagons, bbox, country, city, CONNECTION_STRING)

    output_filename = os.path.join(OUTPUT_DIRECTORY, f"{country}_{city}")

    visualize_hexagons(hexagons, output_filename)

    with open(f"{output_filename}.json", "w", encoding="utf-8") as f:
        json.dump(hexagons, f, indent=2)

def main() -> None:
    os.makedirs(INPUT_DIRECTORY, exist_ok=True)
    os.makedirs(OUTPUT_DIRECTORY, exist_ok=True)

    geojsons = glob.glob(os.path.join(INPUT_DIRECTORY, "*.geojson"))

    if not geojsons:
        print("No .geojson files found in", INPUT_DIRECTORY)
        return

    print(f"Found {len(geojsons)} files to process.")

    for filepath in geojsons:
        process_file(filepath)

if __name__ == "__main__":
    main()
