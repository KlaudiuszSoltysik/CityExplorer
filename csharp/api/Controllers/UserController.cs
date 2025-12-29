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
    public async Task<IActionResult> ValidateLoginToken([FromBody] ValidateLoginTokenRequestDto requestDto)
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

            var googlePayload = await GoogleJsonWebSignature.ValidateAsync(requestDto.GoogleToken, validationSettings);
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
                    Email = googlePayload.Email
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

            return Ok(new ValidateLoginTokenResponseDto
            {
                IsSuccess = true,
                Token = newAppToken
            });
        }
        catch (Exception)
        {
            return Unauthorized("Invalid token or session expired.");
        }
    }

    // Validates if the provided session token is active and valid
    [HttpPost("get-logged-user")]
    public async Task<IActionResult> ValidateAuthorizationToken(
        [FromHeader(Name = "Authorization")] string authorization)
    {
        if (string.IsNullOrEmpty(authorization))
            return Unauthorized("Missing token.");

        var token = authorization.Replace("Bearer ", "").Trim();

        var session = await postgresContext.Sessions
            .AsNoTracking()
            .Include(s => s.User)
            .FirstOrDefaultAsync(s => s.Token == token);

        if (session == null || session.ExpiresAt < DateTime.UtcNow)
            return Unauthorized("Invalid token or session expired.");

        return Ok(new ValidateAuthorizationTokenResponseDto
        {
            Id = session.User.Id
        });
    }

    // Returns user statistics for given city
    [HttpGet("get-user-statistics")]
    public async Task<IActionResult> GetUserStatistics(
        [FromHeader(Name = "Authorization")] string authorization,
        [FromQuery] GetHexagonsFromCityRequestDto requestDto)
    {
        if (string.IsNullOrEmpty(authorization))
            return Unauthorized("Missing token.");

        var token = authorization.Replace("Bearer ", "").Trim();

        var session = await postgresContext.Sessions
            .AsNoTracking()
            .Include(s => s.User)
            .FirstOrDefaultAsync(s => s.Token == token);

        if (session == null || session.ExpiresAt < DateTime.UtcNow)
            return Unauthorized("Invalid token or session expired.");

        var user = session.User;

        var userStatistics = await postgresContext
            .Set<UserCityProgress>()
            .FirstOrDefaultAsync(u => u.UserId == user.Id && u.CityId == requestDto.City);

        if (userStatistics == null)
            return Ok(new GetUserStatisticsResponseDto
            {
                Explored = 0.0,
                Progress = 0,
                HexagonCount = 0,
                PlayTime = 0,
                Distance = 0,
                Ranking = 0,
                UserCount = 0
            });

        var totalUsers = await postgresContext.Set<UserCityProgress>()
            .CountAsync(x => x.CityId == requestDto.City);

        var betterPlayersCount = await postgresContext.Set<UserCityProgress>()
            .CountAsync(x => x.CityId == requestDto.City && x.Progress > userStatistics.Progress);

        var hexagonCount = await postgresContext.Hexagons
            .CountAsync(h => h.CityId == requestDto.City);

        var exploredSum = await postgresContext.Progresses
            .Where(p => p.UserId == user.Id && p.Hexagon != null && p.Hexagon.CityId == requestDto.City)
            .SumAsync(p => p.Progress * p.Hexagon!.Weight);

        return Ok(new GetUserStatisticsResponseDto
        {
            Explored = Math.Round(exploredSum * 100, 2, MidpointRounding.AwayFromZero),
            Progress = userStatistics.Progress,
            HexagonCount = hexagonCount,
            PlayTime = userStatistics.PlayTime,
            Distance = (int)userStatistics.Distance,
            Ranking = betterPlayersCount + 1,
            UserCount = totalUsers
        });
    }

    // Deletes users account
    [HttpPost("delete-user-account")]
    public async Task<IActionResult> DeleteUserAccount([FromHeader(Name = "Authorization")] string authorization)
    {
        await using var transaction = await postgresContext.Database.BeginTransactionAsync();

        try
        {
            if (string.IsNullOrEmpty(authorization))
                return Unauthorized("Missing token.");

            var token = authorization.Replace("Bearer ", "").Trim();

            var session = await postgresContext.Sessions
                .Include(s => s.User)
                .ThenInclude(u => u.HexagonProgresses)
                .FirstOrDefaultAsync(s => s.Token == token);

            if (session == null || session.ExpiresAt < DateTime.UtcNow)
                return Unauthorized("Invalid token or session expired.");

            var user = session.User;

            postgresContext.Users.Remove(user);

            await postgresContext.SaveChangesAsync();

            await transaction.CommitAsync();

            return NoContent();
        }
        catch (Exception)
        {
            await transaction.RollbackAsync();

            return StatusCode(500);
        }
    }
}