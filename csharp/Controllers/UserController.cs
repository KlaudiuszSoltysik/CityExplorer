using csharp.Dtos;
using csharp.Models;
using csharp.Utils;
using Google.Apis.Auth;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace csharp.Controllers;

[Route("user")]
[ApiController]
public class UserController(PostgresContext postgresContext, IConfiguration configuration) : ControllerBase
{
    // Handles Google Sign-In verification and creates a local application session
    [HttpPost("validate-login-token")]
    public async Task<IActionResult> ValidateLoginToken([FromBody] string googleToken)
    {
        try
        {
            var validationSettings = new GoogleJsonWebSignature.ValidationSettings
            {
                Audience = new List<string>
                {
                    configuration["GoogleAuth:ClientId"] ??
                    throw new InvalidOperationException("Google ClientId missing in config")
                }
            };

            var googlePayload = await GoogleJsonWebSignature.ValidateAsync(googleToken, validationSettings);
            var userId = googlePayload.Subject;

            var newAppToken = JwtTokenService.CreateAppJwtToken(userId, googlePayload.Email, configuration);

            var user = await postgresContext.Users
                .Include(u => u.ActiveSession)
                .FirstOrDefaultAsync(u => u.Id == userId);

            if (user == null)
            {
                user = new UserModel
                {
                    Id = userId,
                    Email = googlePayload.Email,
                };
                postgresContext.Users.Add(user);
            }

            if (user.ActiveSession == null)
            {
                var newSession = new SessionModel
                {
                    Token = newAppToken,
                    User = user,
                    UserId = user.Id,
                    ExpiresAt = DateTime.UtcNow.AddDays(30)
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
            return Unauthorized(new LoginResponseDto { IsSuccess = false });
        }
        catch (Exception)
        {
            return StatusCode(500, "An internal error occurred during login.");
        }
    }

    // Validates if the provided session token is active and valid
    [HttpPost("get-logged-user")]
    public async Task<IActionResult> ValidateAuthorizationToken([FromBody] string token)
    {
        var session = await postgresContext.Sessions
            .AsNoTracking()
            .Include(s => s.User)
            .FirstOrDefaultAsync(s => s.Token == token);

        if (session == null || session.ExpiresAt < DateTime.UtcNow)
            return Ok(new AuthorizationResponseDto { IsAuthorized = false });

        return Ok(new AuthorizationResponseDto
        {
            IsAuthorized = true,
            UserDto = new GetUserResponseDto
            {
                Id = session.User.Id
            }
        });
    }
}