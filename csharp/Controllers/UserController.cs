using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using csharp.Dtos;
using Google.Apis.Auth;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

namespace csharp.Controllers;

[Route("user")]
[ApiController]
public class UserController(PostgresContext postgresContext, IConfiguration configuration) : ControllerBase
{
    [HttpPost("validate-login-token")]
    public async Task<IActionResult> ValidateLoginToken([FromBody] LoginRequestDto loginRequestDto)
    {
        try
        {
            var settings = new GoogleJsonWebSignature.ValidationSettings()
            {
                Audience = new List<string>()
                    {
                        configuration["GoogleAuth:ClientId"] ?? throw new InvalidOperationException()
                    }
            };

            var payload = await GoogleJsonWebSignature.ValidateAsync(loginRequestDto.Token, settings);

            // --- At this point, the user is verified by Google ---
            // payload.Email contains the user's email
            // payload.Subject contains Google's unique User ID

            var tokenHandler = new JwtSecurityTokenHandler();
            var key = Encoding.UTF8.GetBytes(configuration["JwtSettings:SecretKey"] ?? throw new InvalidOperationException());

            var tokenDescriptor = new SecurityTokenDescriptor
            {
                Subject = new ClaimsIdentity([
                    new Claim(ClaimTypes.Email, payload.Email),
                    new Claim(ClaimTypes.NameIdentifier, payload.Subject),
                    new Claim("GooglePictureUrl", payload.Picture)
                ]),
                Expires = DateTime.UtcNow.AddDays(30),
                Issuer = configuration["JwtSettings:Issuer"],
                Audience = configuration["JwtSettings:Audience"],
                SigningCredentials = new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
            };

            var token = tokenHandler.CreateToken(tokenDescriptor);
            var appToken = tokenHandler.WriteToken(token);

            return Ok(new LoginResponseDto
            {
                IsSuccess = true,
                Token = appToken
            });
        }
        catch (InvalidJwtException)
        {
            return Unauthorized(new LoginResponseDto { IsSuccess = false, Token = null });
        }
        catch (Exception ex)
        {
            return StatusCode(500, $"Error: {ex.Message}");
        }
    }
}