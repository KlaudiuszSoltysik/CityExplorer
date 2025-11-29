using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using csharp.Dtos;
using csharp.Models;
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
            var settings = new GoogleJsonWebSignature.ValidationSettings
            {
                Audience = new List<string>
                {
                    configuration["GoogleAuth:ClientId"] ?? throw new InvalidOperationException()
                }
            };

            var payload = await GoogleJsonWebSignature.ValidateAsync(loginRequestDto.Token, settings);
            var userId = payload.Subject;

            var user = await postgresContext.Users
                .Include(u => u.ActiveSession)
                .FirstOrDefaultAsync(u => u.Id == userId);

            if (user == null)
            {
                user = new UserModel { Id = userId };
                postgresContext.Users.Add(user);
            }

            var newAppToken = CreateAppJwtToken(payload);
            Console.WriteLine(newAppToken.Length);

            if (user.ActiveSession == null)
            {
                var newSession = new SessionModel
                {
                    Token = newAppToken,
                    User = user,
                    UserId = user.Id
                };
                postgresContext.Sessions.Add(newSession);
            }
            else
            {
                user.ActiveSession.Token = newAppToken;
                user.ActiveSession.ExpiresAt = DateTime.UtcNow.AddDays(30);
            }

            await postgresContext.SaveChangesAsync();

            return Ok(new LoginResponseDto
            {
                IsSuccess = true,
                Token = newAppToken
            });
        }
        catch (InvalidJwtException)
        {
            return Unauthorized(new LoginResponseDto
            {
                IsSuccess = false
            });
        }
        catch (Exception)
        {
            return StatusCode(500, "An internal error occurred during login.");
        }
    }

    private string CreateAppJwtToken(GoogleJsonWebSignature.Payload payload)
    {
        var tokenHandler = new JwtSecurityTokenHandler();
        var key = Encoding.UTF8.GetBytes(
            configuration["JwtSettings:SecretKey"] ?? throw new InvalidOperationException());

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
            SigningCredentials =
                new SigningCredentials(new SymmetricSecurityKey(key), SecurityAlgorithms.HmacSha256Signature)
        };

        var token = tokenHandler.CreateToken(tokenDescriptor);
        var appToken = tokenHandler.WriteToken(token);
        return appToken;
    }
}