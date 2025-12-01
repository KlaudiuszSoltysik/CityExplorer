using System.ComponentModel.DataAnnotations;

namespace csharp.Models;

public class VersionsModel
{
    [Key] [MaxLength(100)] public string Key { get; init; } = string.Empty;
    public int VersionNumber { get; init; }
}