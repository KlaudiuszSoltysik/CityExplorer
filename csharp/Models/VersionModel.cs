using System.ComponentModel.DataAnnotations;

namespace csharp.Models;

public class VersionModel
{
    [Key] [MaxLength(100)] public string Key { get; init; } = string.Empty;
    [MaxLength(10)] public required string VersionNumber { get; init; }
}