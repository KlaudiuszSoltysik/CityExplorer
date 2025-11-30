namespace csharp.Dtos;

public class LoginRequestDto
{
    public required string Token { get; set; }
}

public class LoginResponseDto
{
    public bool IsSuccess { get; set; }
    public string? Token { get; set; }
}

public class AuthorizationRequestDto
{
    public required string Token { get; set; }
}

public class AuthorizationResponseDto
{
    public bool IsAuthorized { get; set; }
    public UserDto? UserDto { get; set; }
}

public class UserDto
{
    public string Id { get; set; } = string.Empty;
}