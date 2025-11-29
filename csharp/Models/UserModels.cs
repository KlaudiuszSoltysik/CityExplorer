using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace csharp.Models;

public class UserModel
{
    [Key] [MaxLength(200)] public string Id { get; init; } = string.Empty;

    public SessionModel? ActiveSession { get; init; }
    // TODO: Implement other shit
}

public class SessionModel
{
    [Key] public int Id { get; init; }
    public DateTime ExpiresAt { get; set; } = DateTime.UtcNow.AddDays(30);
    [MaxLength(500)] public string? Token { get; set; }
    [MaxLength(200)] public required string UserId { get; init; }
    [ForeignKey(nameof(UserId))] public required UserModel User { get; init; }
}