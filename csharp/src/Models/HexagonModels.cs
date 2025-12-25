using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace csharp.Models;

public class HexagonModel
{
    [Key] [MaxLength(50)] public string Id { get; init; } = string.Empty;
    public required List<List<double>> Boundaries { get; init; }
    public required List<double> Center { get; init; }
    [MaxLength(100)] public string CityId { get; init; } = string.Empty;
    public required CityModel City { get; init; }
    public List<PoiModel> Pois { get; init; } = [];
    public double Weight { get; init; }
}

public class PoiModel
{
    [Key] [MaxLength(100)] public string Id { get; init; } = string.Empty;
    [MaxLength(200)] public string? Name { get; init; }
    [MaxLength(50)] public string PoiType { get; init; } = string.Empty;
    [MaxLength(50)] public string PoiSubtype { get; init; } = string.Empty;
    public List<double>? Location { get; init; }
    public List<List<double>>? Boundary { get; init; }
    public bool IsPromoted { get; init; }
    [MaxLength(50)] public string? HexagonId { get; init; }
    [ForeignKey(nameof(HexagonId))] public required HexagonModel Hexagon { get; init; }
    [MaxLength(50)] public string CityId { get; init; } = string.Empty;
    [ForeignKey(nameof(CityId))] public required CityModel City { get; init; }
}

public class CityModel
{
    [Key] [MaxLength(50)] public string City { get; init; } = string.Empty;
    [MaxLength(50)] public string Country { get; init; } = string.Empty;
    public required List<double> Bbox { get; init; }
    public ICollection<HexagonModel> Hexagons { get; init; } = [];
}

[PrimaryKey(nameof(UserId), nameof(HexagonId))]
public class UserHexagonProgress
{
    [MaxLength(200)] public string UserId { get; init; } = string.Empty;
    [ForeignKey(nameof(UserId))] public UserModel? User { get; init; }
    [MaxLength(50)] public string HexagonId { get; init; } = string.Empty;
    [ForeignKey(nameof(HexagonId))] public HexagonModel? Hexagon { get; init; }
    public double Progress { get; set; }
}