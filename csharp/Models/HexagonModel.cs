using System.ComponentModel.DataAnnotations;

namespace csharp.Models;

public class HexagonModel
{
    [Key] public string Id { get; set; } = string.Empty;
    public string Country { get; set; } = string.Empty;
    public string City { get; set; } = string.Empty;
    public List<PoiModel> TouristPois { get; set; } = [];
    public List<PoiModel> LocalPois { get; set; } = [];
    public double TouristWeight { get; set; }
    public double LocalWeight { get; set; }
}