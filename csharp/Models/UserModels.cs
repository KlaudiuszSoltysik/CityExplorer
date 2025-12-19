using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace csharp.Models;

public class UserModel
{
    [Key] [MaxLength(200)] public string Id { get; init; } = string.Empty;
    [MaxLength(100)] public string Email { get; init; } = string.Empty;
    public SessionModel? ActiveSession { get; init; }
    public List<UserHexagonProgress> HexagonProgresses { get; set; } = [];
    public List<UserCityProgress> CityProgresses { get; set; } = [];
}

public class SessionModel
{
    [Key] public int Id { get; init; }
    public DateTime ExpiresAt { get; set; } = DateTime.UtcNow.AddDays(30);
    [MaxLength(500)] public string? Token { get; set; }
    [MaxLength(200)] public required string UserId { get; init; }
    [ForeignKey(nameof(UserId))] public required UserModel User { get; init; }
}

[Index(nameof(CityId), nameof(Progress))]
public class UserCityProgress
{
    [Key] public int Id { get; set; }
    [Required] [MaxLength(50)] public string CityId { get; set; } = string.Empty;
    public int Progress { get; set; }
    public int PlayTime { get; set; }
    [Required] [MaxLength(200)] public string UserId { get; set; } = string.Empty;
    [ForeignKey(nameof(UserId))] public UserModel? User { get; set; }
}