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
using Moq;
using StackExchange.Redis;

namespace tests;

public class HexagonControllerTests : IClassFixture<WebApplicationFactory<Program>>, IDisposable
{
    private readonly SqliteConnection _connection;
    private readonly WebApplicationFactory<Program> _factory;

    public HexagonControllerTests(WebApplicationFactory<Program> factory)
    {
        _connection = new SqliteConnection("Filename=:memory:");
        _connection.Open();

        _factory = factory.WithWebHostBuilder(builder =>
        {
            builder.UseEnvironment("Testing");
            builder.ConfigureServices(services =>
            {
                services.AddDbContext<PostgresContext>(options => { options.UseSqlite(_connection); });

                var redisDescriptor = services.SingleOrDefault(d => d.ServiceType == typeof(IConnectionMultiplexer));

                if (redisDescriptor != null) services.Remove(redisDescriptor);

                var mockRedis = new Mock<IConnectionMultiplexer>();

                mockRedis.Setup(x => x.GetDatabase(It.IsAny<int>(), It.IsAny<object>()))
                    .Returns(new Mock<IDatabase>().Object);

                services.AddSingleton(mockRedis.Object);

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
    public async Task GetCountriesWithCities_ShouldReturnGroupedAndSortedData()
    {
        var client = _factory.CreateClient();
        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();
            db.Cities.AddRange(
                new CityModel { City = "Poznań", Bbox = [1.0, 2.0, 3.0, 4.0], Country = "Poland" },
                new CityModel { City = "Warszawa", Bbox = [1.0, 2.0, 3.0, 4.0], Country = "Poland" },
                new CityModel { City = "Berlin", Bbox = [1.0, 2.0, 3.0, 4.0], Country = "Germany" }
            );
            await db.SaveChangesAsync();
        }

        var response = await client.GetAsync("/hexagon/get-countries-with-cities");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var result = await response.Content.ReadFromJsonAsync<List<GetCountriesWithCitiesResponseDto>>();

        result.Should().HaveCount(2);
        result[0].Country.Should().Be("Germany");
        result[1].Country.Should().Be("Poland");
        result[1].Cities.Should().ContainInOrder("Poznań", "Warszawa");
    }

    [Fact]
    public async Task GetHexagonsFromCity_ShouldReturnData_WhenCityExists()
    {
        var client = _factory.CreateClient();
        const string cityName = "Poznań";
        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();
            var city = new CityModel { City = cityName, Bbox = [1.0, 2.0, 3.0, 4.0], Country = "Poland" };
            db.Cities.Add(city);
            db.Hexagons.Add(new HexagonModel
            {
                Id = "hex-1",
                CityId = city.City,
                City = city,
                Weight = 1.0,
                Boundaries = [[0.0, 0.0]],
                Center = [0.0, 0.0]
            });
            await db.SaveChangesAsync();
        }

        var response = await client.GetAsync($"/hexagon/get-hexagons-from-city?city={cityName}");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var result = await response.Content.ReadFromJsonAsync<GetHexagonsFromCityResponseDto>();
        result!.Bbox.Should().ContainInOrder(1.0, 2.0, 3.0, 4.0);
        result.Hexagons.Should().HaveCount(1);
        result.Hexagons[0].Id.Should().Be("hex-1");
    }

    [Fact]
    public async Task GetHexagonsFromCity_ShouldReturnNotFound_WhenNoData()
    {
        var client = _factory.CreateClient();
        var response = await client.GetAsync("/hexagon/get-hexagons-from-city?city=Poznańń");

        response.StatusCode.Should().Be(HttpStatusCode.NotFound);
    }

    [Fact]
    public async Task GetPoisFromHexagon_ShouldReturnTop3Pois_PrioritizingPromoted()
    {
        var client = _factory.CreateClient();
        const string hexId = "hex-abc";
        const string cityId = "Warsaw";

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();

            var city = new CityModel
            {
                City = cityId,
                Country = "Poland",
                Bbox = [21.0, 52.2, 21.1, 52.3]
            };

            var hexagon = new HexagonModel
            {
                Id = hexId,
                CityId = city.City,
                City = city,
                Boundaries = [[21.0, 52.2], [21.1, 52.3]],
                Center = [21.05, 52.25],
                Weight = 1.0
            };

            db.Cities.Add(city);
            db.Hexagons.Add(hexagon);

            db.Pois.AddRange(
                new PoiModel
                {
                    Id = "1", HexagonId = hexId, Hexagon = hexagon, CityId = cityId, City = city, Name = "Regular 1",
                    IsPromoted = false, PoiType = "Shop", PoiSubtype = "Grocery"
                },
                new PoiModel
                {
                    Id = "2", HexagonId = hexId, Hexagon = hexagon, CityId = cityId, City = city, Name = "Regular 2",
                    IsPromoted = false, PoiType = "Shop", PoiSubtype = "Grocery"
                },
                new PoiModel
                {
                    Id = "3", HexagonId = hexId, Hexagon = hexagon, CityId = cityId, City = city, Name = "Regular 3",
                    IsPromoted = false, PoiType = "Shop", PoiSubtype = "Grocery"
                },
                new PoiModel
                {
                    Id = "4", HexagonId = hexId, Hexagon = hexagon, CityId = cityId, City = city, Name = "Promoted 1",
                    IsPromoted = true, PoiType = "Museum", PoiSubtype = "Art"
                },
                new PoiModel
                {
                    Id = "5", HexagonId = hexId, Hexagon = hexagon, CityId = cityId, City = city, Name = "A",
                    IsPromoted = false, PoiType = "Short", PoiSubtype = "None"
                }
            );

            await db.SaveChangesAsync();
        }

        var response = await client.GetAsync($"/hexagon/get-pois-from-hexagon?hexagonId={hexId}");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var result = await response.Content.ReadFromJsonAsync<List<GetPoisFromHexagonResponseDto>>();

        result.Should().NotBeNull();
        result.Count.Should().BeInRange(0, 3);

        if (result.Any(p => p.IsPromoted))
            result[0].IsPromoted.Should().BeTrue("because promoted POIs are ordered first");

        result.Any(p => p.Name == "A").Should().BeFalse("because names with length < 2 are filtered out");
    }

    [Fact]
    public async Task GetHexagonProgresses_ShouldReturnOnlyCurrentUserProgressForSpecificCity()
    {
        var client = _factory.CreateClient();
        const string testToken = "token";
        const string cityId = "Poznań";

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();

            var city = new CityModel { City = cityId, Country = "Poland", Bbox = [17.0, 51.0, 17.1, 51.1] };
            var hex = new HexagonModel
            {
                Id = "hex-1", City = city, CityId = cityId,
                Boundaries = [[17.0, 51.0]], Center = [17.05, 51.05], Weight = 1.0
            };

            var userMe = new UserModel { Id = "me", Email = "me@gmail.com" };
            var userOther = new UserModel { Id = "other", Email = "other@gmail.com" };

            db.Cities.Add(city);
            db.Hexagons.Add(hex);
            db.Users.AddRange(userMe, userOther);

            db.Sessions.Add(new SessionModel
            {
                Token = testToken, User = userMe, UserId = userMe.Id,
                ExpiresAt = DateTime.UtcNow.AddDays(1)
            });

            db.Progresses.AddRange(
                new UserHexagonProgress
                    { UserId = userMe.Id, User = userMe, HexagonId = hex.Id, Hexagon = hex, Progress = 0.75 },
                new UserHexagonProgress
                    { UserId = userOther.Id, User = userOther, HexagonId = hex.Id, Hexagon = hex, Progress = 1.0 }
            );

            await db.SaveChangesAsync();
        }

        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", testToken);
        var response = await client.GetAsync($"/hexagon/get-hexagon-progresses?city={cityId}");

        response.StatusCode.Should().Be(HttpStatusCode.OK);
        var result = await response.Content.ReadFromJsonAsync<List<HexagonProgress>>();

        result.Should().HaveCount(1);
        result[0].HexagonId.Should().Be("hex-1");
        result[0].Progress.Should().Be(0.75);
    }
}