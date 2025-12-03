using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using Microsoft.EntityFrameworkCore;

namespace csharp.Models;

public class UserModel
{
    [Key] [MaxLength(200)] public string Id { get; init; } = string.Empty;
    public SessionModel? ActiveSession { get; init; }
    public List<UserHexagonProgress> HexagonProgresses { get; set; } = [];
}

public class SessionModel
{
    [Key] public int Id { get; init; }
    public DateTime ExpiresAt { get; set; } = DateTime.UtcNow.AddDays(30);
    [MaxLength(500)] public string? Token { get; set; }
    [MaxLength(200)] public required string UserId { get; init; }
    [ForeignKey(nameof(UserId))] public required UserModel User { get; init; }
}

[PrimaryKey(nameof(UserId), nameof(HexagonId))]
public class UserHexagonProgress
{
    [MaxLength(200)] public string UserId { get; init; } = string.Empty;
    [ForeignKey(nameof(UserId))] public UserModel? User { get; init; }
    [MaxLength(50)] public string HexagonId { get; init; } = string.Empty;
    [ForeignKey(nameof(HexagonId))] public HexagonModel? Hexagon { get; init; }
    public double AccumulatedSeconds { get; set; }
    public bool IsCollected { get; set; }
}