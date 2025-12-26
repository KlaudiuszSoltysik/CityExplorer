import pytest
import h3
import os
import json

from functions import build_overpass_query, _extract_location, _parse_poi_element, fetch_pois, _hexagons_from_coords, \
    _hexagons_from_bbox, assign_pois_to_hexagons, attach_boundaries, calculate_weights, visualize_hexagons, \
    load_region_data

def test_build_overpass_query_formats_correctly():
    bbox = (52.1, 21.0, 52.2, 21.1)
    filters = {
        "tourism": "museum",
        "amenity": "cafe",
        "leisure": "park",
        "man_made": "tower",
        "natural": "beach",
        "waterway": "waterfall",
        "craft": "brewery"
    }

    query = build_overpass_query(bbox, filters)

    assert "52.1,21.0,52.2,21.1" in query

    assert 'nwr["tourism"~"museum"]' in query
    assert 'node["natural"~"beach"]' in query

    assert 'nwr["historic"]' in query

    assert "[out:json][timeout:10000];" in query

def test_extract_location_from_lat_lon():
    element = {"lat": 52.4, "lon": 16.9}
    result = _extract_location(element)
    assert result == (52.4, 16.9)

def test_extract_location_from_center():
    element = {
        "center": {"lat": 52.406, "lon": 16.925}
    }
    result = _extract_location(element)
    assert result == (52.406, 16.925)

def test_extract_location_missing_data():
    assert _extract_location({}) is None
    assert _extract_location({"type": "way"}) is None
    assert _extract_location({"center": {}}) is None

@pytest.fixture
def mock_poi_keys():
    return {
        "tourism": 4,
        "historic": 4,
        "amenity": 2
    }

def test_parse_poi_element_success(mock_poi_keys):
    element = {
        "type": "node",
        "id": 123,
        "lat": 52.4,
        "lon": 16.9,
        "tags": {
            "name": "Old Town Hall",
            "tourism": "attraction",
            "amenity": "townhall"
        }
    }

    poi = _parse_poi_element(element, mock_poi_keys, "Poznań")

    assert poi is not None
    assert poi["id"] == "node/123"
    assert poi["poi_type"] == "tourism"
    assert poi["poi_subtype"] == "attraction"
    assert poi["name"] == "Old Town Hall"
    assert poi["location"] == (52.4, 16.9)

def test_parse_poi_element_fallback_name(mock_poi_keys):
    element = {
        "type": "node",
        "id": 456,
        "lat": 52.0,
        "lon": 16.0,
        "tags": {
            "name:en": "English Name",
            "historic": "monument"
        }
    }

    poi = _parse_poi_element(element, mock_poi_keys, "Poznań")
    assert poi["name"] == "English Name"

def test_parse_poi_element_geometry_mapping(mock_poi_keys):
    element = {
        "type": "way",
        "id": 789,
        "tags": {"tourism": "zoo", "name": "Zoo"},
        "geometry": [
            {"lat": 52.1, "lon": 16.1},
            {"lat": 52.2, "lon": 16.2}
        ]
    }

    poi = _parse_poi_element(element, mock_poi_keys, "Poznań")

    assert "boundary" in poi
    assert len(poi["boundary"]) == 2
    assert poi["boundary"][0] == (52.1, 16.1)

def test_parse_poi_element_returns_none_for_irrelevant_tags(mock_poi_keys):
    element = {
        "type": "node",
        "id": 999,
        "tags": {"shop": "supermarket"}
    }

    assert _parse_poi_element(element, mock_poi_keys, "Poznań") is None

def test_fetch_pois_successful_flow(requests_mock):
    bbox = (52.1, 21.0, 52.2, 21.1)
    poi_keys = {"tourism": 4}
    overpass_filters = {"tourism": "museum"}
    city = "TestCity"

    mock_response = {
        "elements": [
            {
                "type": "node",
                "id": 1,
                "lat": 52.15,
                "lon": 21.05,
                "tags": {"name": "Mock Museum", "tourism": "museum"}
            }
        ]
    }

    requests_mock.post("https://overpass-api.de/api/interpreter", json=mock_response)

    results = fetch_pois(bbox, poi_keys, overpass_filters, city)

    assert len(results) == 1
    assert results[0]["name"] == "Mock Museum"

def test_fetch_pois_network_error(requests_mock):
    requests_mock.post("https://overpass-api.de/api/interpreter", status_code=500)

    results = fetch_pois((0,0,1,1), {}, {}, "City")

    assert results == []

def test_hexagons_from_coords_returns_cells():
    coords = [
        (52.40, 16.90),
        (52.41, 16.90),
        (52.41, 16.91),
        (52.40, 16.90)
    ]
    res = 9

    cells = _hexagons_from_coords(coords, res)

    assert len(cells) > 0
    assert h3.is_valid_cell(list(cells)[0])

def test_hexagons_from_bbox_structure():
    bbox = (52.0, 16.0, 52.1, 16.1)
    res = 9

    cells = _hexagons_from_bbox(bbox, res)

    assert len(cells) > 0
    assert isinstance(list(cells)[0], str)
    assert len(list(cells)[0]) == 15

def test_assign_pois_to_hexagons_mixed_types():
    res = 9
    hex_1 = h3.latlng_to_cell(52.40, 16.90, res)
    active_hexagons = {hex_1}

    hex_boundary = list(h3.cell_to_boundary(hex_1))
    hex_boundary.append(hex_boundary[0])

    pois = [
        {
            "id": "node/1",
            "location": h3.cell_to_latlng(hex_1),
            "name": "Point POI"
        },
        {
            "id": "way/1",
            "boundary": hex_boundary,
            "name": "Area POI"
        }
    ]

    result = assign_pois_to_hexagons(active_hexagons, pois, res)

    data_hex_1 = next(h for h in result if h["id"] == hex_1)

    assert len(data_hex_1["pois"]) == 2
    assert data_hex_1["pois"][0]["id"].startswith("node/1#")
    assert data_hex_1["pois"][1]["id"].startswith("way/1#")

def test_attach_boundaries_adds_geo_data():
    hex_id = "891e2040003ffff"
    hexagons_data = [
        {"id": hex_id, "pois": []}
    ]

    result = attach_boundaries(hexagons_data)

    item = result[0]

    assert "boundaries" in item
    assert "center" in item

    assert len(item["boundaries"]) == 7
    assert item["boundaries"][0] == item["boundaries"][-1]

    assert len(item["center"]) == 2
    assert isinstance(item["center"][0], float)
    assert isinstance(item["boundaries"][0][0], float)

def test_attach_boundaries_empty_list():
    assert attach_boundaries([]) == []

def test_calculate_weights_logic():
    poi_keys = {"tourism": 4, "amenity": 2}
    poi_factor = 0.5

    hexagons = [
        {"id": "rich", "pois": [{"poi_type": "tourism"}]},
        {"id": "medium", "pois": [{"poi_type": "amenity"}]},
        {"id": "empty", "pois": []}
    ]

    result = calculate_weights(hexagons, poi_factor, poi_keys)

    total_weight = sum(h["weight"] for h in result)
    assert total_weight == pytest.approx(1.0)

    assert result[0]["weight"] > result[1]["weight"]
    assert result[1]["weight"] > result[2]["weight"]

    assert result[2]["weight"] == pytest.approx(0.1666, abs=1e-3)

def test_calculate_weights_no_hexagons():
    assert calculate_weights([], 0.5, {}) == []

def test_calculate_weights_zero_factor():
    hexagons = [
        {"id": "a", "pois": [{"poi_type": "tourism"}]},
        {"id": "b", "pois": []}
    ]
    result = calculate_weights(hexagons, 0.0, {"tourism": 4})

    assert result[0]["weight"] == result[1]["weight"]
    assert result[0]["weight"] == pytest.approx(0.5)

def test_save_to_db_calls_correct_sql(mocker):
    mock_connect = mocker.patch("psycopg2.connect")
    mock_conn = mock_connect.return_value.__enter__.return_value
    mock_cursor = mock_conn.cursor.return_value.__enter__.return_value

    hexagons = [{
        "id": "hex1",
        "boundaries": [[1, 2]],
        "center": [1, 2],
        "weight": 0.5,
        "pois": [{
            "id": "poi1",
            "name": "Test POI",
            "poi_type": "tourism",
            "poi_subtype": "museum",
            "location": [1.1, 2.2]
        }]
    }]
    bbox = (1, 2, 3, 4)

    from main import save_to_db
    save_to_db(hexagons, bbox, "Poland", "Poznan", "fake_connection_string")

    mock_connect.assert_called_once_with("fake_connection_string")

    city_call = mock_cursor.execute.call_args_list[0]
    assert "INSERT INTO \"Cities\"" in city_call[0][0]
    assert city_call[0][1][0] == "Poznan"

    hex_call = mock_cursor.execute.call_args_list[1]
    assert "INSERT INTO \"Hexagons\"" in hex_call[0][0]
    assert hex_call[0][1][0] == "hex1"

    mock_conn.commit.assert_called_once()

def test_load_region_data_parsing(tmp_path):
    sample_geojson = {
        "features": [{
            "geometry": {
                "coordinates": [[[[16.9, 52.4], [16.91, 52.41], [16.92, 52.42]]]]
            }
        }]
    }

    d = tmp_path / "data"
    d.mkdir()
    p = d / "test_poznan.geojson"
    p.write_text(json.dumps(sample_geojson))

    coords, bbox = load_region_data(str(p))

    assert coords[0] == (52.4, 16.9)
    assert coords[1] == (52.41, 16.91)

    assert bbox == (52.4, 16.9, 52.42, 16.92)

def test_load_region_data_file_not_found():
    with pytest.raises(FileNotFoundError):
        load_region_data("non_existent_file.geojson")
