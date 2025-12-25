using System.Net;
using csharp;
using csharp.Models;
using FluentAssertions;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;

namespace tests;

public class VersionControllerTests : IClassFixture<WebApplicationFactory<Program>>, IDisposable
{
    private readonly WebApplicationFactory<Program> _factory;
    private readonly SqliteConnection _connection;

    public VersionControllerTests(WebApplicationFactory<Program> factory)
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
    public async Task GetCurrentVersion_ShouldReturnZero_WhenKeyDoesNotExist()
    {
        var client = _factory.CreateClient();

        var response = await client.GetAsync("/version/get-current-version?key=non-existent");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var content = await response.Content.ReadAsStringAsync();
        content.Should().Be("0");
    }

    [Fact]
    public async Task GetCurrentVersion_ShouldReturnCorrectVersion_WhenKeyExists()
    {
        var client = _factory.CreateClient();

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();
            db.Versions.Add(new VersionModel { Key = "get-countries-with-cities", VersionNumber = "1" });
            await db.SaveChangesAsync();
        }

        var response = await client.GetAsync("/version/get-current-version?key=get-countries-with-cities");

        var content = await response.Content.ReadAsStringAsync();
        content.Should().Be("1");
    }
}
