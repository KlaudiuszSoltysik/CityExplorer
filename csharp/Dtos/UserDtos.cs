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