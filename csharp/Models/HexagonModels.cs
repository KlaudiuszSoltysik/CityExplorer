using System.ComponentModel.DataAnnotations;

namespace csharp.Models;

public class HexagonModel
{
    [Key] [MaxLength(50)] public string Id { get; init; } = string.Empty;
    public required List<List<double>> Boundaries { get; init; }
    [MaxLength(100)] public string CityId { get; init; } = string.Empty;
    public required CityModel City { get; init; }
    public List<PoiModel> TouristPois { get; init; } = [];
    public List<PoiModel> LocalPois { get; init; } = [];
    public double TouristWeight { get; init; }
    public double LocalWeight { get; init; }
}

public class PoiModel
{
    [Key] [MaxLength(100)] public string Id { get; init; } = string.Empty;
    [MaxLength(200)] public string? Name { get; init; }
    [MaxLength(50)] public string PoiType { get; init; } = string.Empty;
    [MaxLength(50)] public string PoiSubtype { get; init; } = string.Empty;
    public List<double>? Location { get; init; }
    public List<List<double>>? Boundary { get; init; }
}

public class CityModel
{
    [Key] [MaxLength(50)] public string City { get; init; } = string.Empty;
    [MaxLength(50)] public string Country { get; init; } = string.Empty;
    public required List<double> Bbox { get; init; }
}