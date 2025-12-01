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