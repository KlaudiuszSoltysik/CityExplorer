namespace csharp.Dtos;

public class GetCountriesWithCitiesDto
{
    public string Country { get; set; }
    public List<string> Cities { get; set; }
}

public class GetCityHexagonsDataDto
{
    public List<double> Bbox { get; set; }

    public List<HexagonsDto> Hexagons { get; set; } = [];
}

public class HexagonsDto
{
    public string Id { get; set; }
    public List<List<double>> Boundaries { get; set; }
    public double Weight { get; set; }
}