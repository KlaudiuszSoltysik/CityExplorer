using System.ComponentModel.DataAnnotations;

namespace csharp.Models;

public class CityModel
{
    [Key] public string City { get; set; } = string.Empty;
    public string Country { get; set; } = string.Empty;
    public List<double> Bbox { get; set; }
}