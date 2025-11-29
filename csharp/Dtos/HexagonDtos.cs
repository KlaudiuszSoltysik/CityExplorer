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
    public double Weight { get; set; }
}