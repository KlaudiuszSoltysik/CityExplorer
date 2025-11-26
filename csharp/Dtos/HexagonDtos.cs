namespace csharp.Dtos;

public class GetHexagonsFromCityDto
{
    public string Id { get; set; }
    public List<List<double>> Boundaries { get; set; }
    public double Weight { get; set; }
}

public class GetCountriesWithCitiesDto
{
    public string Country { get; set; }
    public List<string> Cities { get; set; }
}