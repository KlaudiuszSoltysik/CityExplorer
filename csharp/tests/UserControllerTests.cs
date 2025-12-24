using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using csharp;
using csharp.Dtos;
using csharp.Models;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace tests;

public class UserControllerTests : IClassFixture<WebApplicationFactory<Program>>, IDisposable
{
    private readonly WebApplicationFactory<Program> _factory;
    private readonly SqliteConnection _connection;

    public UserControllerTests(WebApplicationFactory<Program> factory)
    {
        _connection = new SqliteConnection("Filename=:memory:");
        _connection.Open();

        _factory = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.ConfigureServices(services =>
            {
                services.AddDbContext<PostgresContext>(options =>
                {
                    options.UseSqlite(_connection);
                });

                using var scope = services.BuildServiceProvider().CreateScope();
                var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();
                db.Database.EnsureCreated();
            });
        });
    }

    public void Dispose()
    {
        _connection.Close();
        _connection.Dispose();
    }

    [Fact]
    public async Task GetLoggedUser_ShouldReturnAuthorized_WhenTokenIsValid()
    {
        var client = _factory.CreateClient();
        const string testToken = "valid-test-token";

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();
            var user = new UserModel { Id = "user-1", Email = "test@example.com" };
            db.Users.Add(user);
            db.Sessions.Add(new SessionModel
            {
                Token = testToken,
                UserId = user.Id,
                User = user,
                ExpiresAt = DateTime.UtcNow.AddDays(1)
            });
            await db.SaveChangesAsync();
        }

        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", testToken);
        var response = await client.PostAsync("/user/get-logged-user", null);

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var result = await response.Content.ReadFromJsonAsync<AuthorizationResponseDto>();
        result!.IsAuthorized.Should().BeTrue();
        result.UserDto!.Id.Should().Be("user-1");
    }

    [Fact]
    public async Task GetUserStatistics_ShouldReturnCorrectRanking()
    {
        var client = _factory.CreateClient();
        const string testToken = "token";
        const string cityId = "Poznań";

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();

            var user1 = new UserModel { Id = "player-1", Email = "p1@gmail.com" };
            var user2 = new UserModel { Id = "player-2", Email = "p2@gmail.com" };
            db.Users.AddRange(user1, user2);

            db.Set<UserCityProgress>().AddRange(
                new UserCityProgress { UserId = "player-1", CityId = cityId, Progress = 10 },
                new UserCityProgress { UserId = "player-2", CityId = cityId, Progress = 50 }
            );

            db.Sessions.Add(new SessionModel
            {
                Token = testToken,
                UserId = "player-1",
                User = user1,
                ExpiresAt = DateTime.UtcNow.AddDays(1)
            });
            await db.SaveChangesAsync();
        }

        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", testToken);
        var response = await client.GetAsync($"/user/get-user-statistics?city={cityId}");

        var stats = await response.Content.ReadFromJsonAsync<GetUserStatisticsDto>();
        stats!.Ranking.Should().Be(2);
        stats.UserCount.Should().Be(2);
    }

    [Fact]
    public async Task DeleteUserAccount_ShouldRemoveUserFromDatabase()
    {
        var client = _factory.CreateClient();
        const string testToken = "delete-token";
        const string userId = "unlucky-user";

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();
            var user = new UserModel { Id = userId, Email = "user@gmail.com" };
            db.Users.Add(user);
            db.Sessions.Add(new SessionModel
            {
                Token = testToken,
                UserId = userId,
                User = user,
                ExpiresAt = DateTime.UtcNow.AddDays(1)
            });
            await db.SaveChangesAsync();
        }

        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", testToken);
        var response = await client.PostAsync("/user/delete-user-account", null);

        response.StatusCode.Should().Be(HttpStatusCode.NoContent);

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();
            var userInDb = await db.Users.FindAsync(userId);
            userInDb.Should().BeNull();
        }
    }
}