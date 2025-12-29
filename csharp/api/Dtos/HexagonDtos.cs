using System.Text.Json.Serialization;

namespace csharp.Dtos;

public class GetCountriesWithCitiesResponseDto
{
    [JsonPropertyName("country")] public required string Country { get; init; }

    [JsonPropertyName("cities")] public required List<string> Cities { get; init; }
}

public class GetHexagonsFromCityRequestDto
{
    [JsonPropertyName("city")] public required string City { get; init; }
}

public class GetHexagonsFromCityResponseDto
{
    [JsonPropertyName("bbox")] public required List<double> Bbox { get; init; }

    [JsonPropertyName("hexagons")] public List<Hexagon> Hexagons { get; init; } = [];
}

public class GetPoisFromHexagonRequestDto
{
    [JsonPropertyName("hexagonId")] public required string HexagonId { get; init; }
}

public class GetPoisFromHexagonResponseDto
{
    [JsonPropertyName("name")] public required string Name { get; init; }

    [JsonPropertyName("type")] public required string Type { get; init; }

    [JsonPropertyName("isPromoted")] public required bool IsPromoted { get; init; }
}

public class PostLocationBatchRequestDto
{
    [JsonPropertyName("locations")] public required List<Location> Locations { get; init; }
}

public class Location
{
    [JsonPropertyName("latitude")] public double Latitude { get; init; }

    [JsonPropertyName("longitude")] public double Longitude { get; init; }

    [JsonPropertyName("timestamp")] public DateTime Timestamp { get; init; }
}

public class PostLocationBatchResponseDto
{
    [JsonPropertyName("updatedHexagons")] public List<HexagonProgress>? UpdatedHexagons { get; init; }

    [JsonPropertyName("token")] public string? Token { get; init; }
}

public class HexagonProgress
{
    [JsonPropertyName("hexagonId")] public string HexagonId { get; init; } = string.Empty;

    [JsonPropertyName("progress")] public double Progress { get; init; }
}

public class Hexagon
{
    [JsonPropertyName("id")] public required string Id { get; init; }

    [JsonPropertyName("boundaries")] public required List<List<double>> Boundaries { get; init; }

    [JsonPropertyName("center")] public required List<double> Center { get; init; }

    [JsonPropertyName("weight")] public double Weight { get; init; }
}

public class GenerateRouteRequestDto
{
    [JsonPropertyName("userLatitude")] public double UserLatitude { get; init; }

    [JsonPropertyName("userLongitude")] public double UserLongitude { get; init; }

    [JsonPropertyName("duration")] public int Duration { get; init; }
}

public class GenerateRouteResponseDto
{
    [JsonPropertyName("jobId")] public required string JobId { get; init; }

    [JsonPropertyName("token")] public string? Token { get; init; }
}

public class WorkerResult
{
    [JsonPropertyName("route")] public List<string> Route { get; init; } = [];
}