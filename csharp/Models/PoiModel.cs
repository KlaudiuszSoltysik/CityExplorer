using System.ComponentModel.DataAnnotations;

namespace csharp.Models;

public class PoiModel
{
    [Key] public string Id { get; set; }
    public string? Name { get; set; }
    public string PoiType { get; set; } = string.Empty;
    public string PoiSubtype { get; set; } = string.Empty;
    public List<double>? Location { get; set; }
    public List<List<double>>? Boundary { get; set; }
}