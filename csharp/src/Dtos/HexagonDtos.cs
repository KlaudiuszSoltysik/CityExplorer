namespace csharp.Dtos;

public class GetCountriesWithCitiesDto
{
    public required string Country { get; init; }
    public required List<string> Cities { get; set; }
}

public class GetCityHexagonsDataDto
{
    public required List<double> Bbox { get; set; }
    public List<HexagonsDto> Hexagons { get; set; } = [];
}

public class HexagonsDto
{
    public required string Id { get; set; }
    public required List<List<double>> Boundaries { get; set; }
    public required List<double> Center { get; set; }
    public double Weight { get; set; }
}

public class GetPoisFromHexagonDto
{
    public required string Name { get; set; }
    public required string Type { get; set; }
    public required bool IsPromoted { get; set; }
}

public class LocationDto
{
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public DateTime Timestamp { get; set; }
}

public class PostLocationBatchDto
{
    public required List<LocationDto> Locations { get; set; }
}

public class HexagonProgressDto
{
    public string HexagonId { get; set; } = string.Empty;
    public double Progress { get; set; }
}

public class SyncResponseDto
{
    public List<HexagonProgressDto>? UpdatedHexagons { get; set; }
    public string? Token { get; set; }
}