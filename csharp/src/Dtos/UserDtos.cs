namespace csharp.Dtos;

public class LoginResponseDto
{
    public bool IsSuccess { get; set; }
    public string? Token { get; set; }
}

public class AuthorizationResponseDto
{
    public bool IsAuthorized { get; set; }
    public GetUserResponseDto? UserDto { get; set; }
}

public class GetUserResponseDto
{
    public string Id { get; set; } = string.Empty;
}

public class GetUserStatisticsDto
{
    public double Explored { get; set; }
    public int Progress { get; set; }
    public int HexagonCount { get; set; }
    public int PlayTime { get; set; }
    public int Distance { get; set; }
    public int Ranking { get; set; }
    public int UserCount { get; set; }
}