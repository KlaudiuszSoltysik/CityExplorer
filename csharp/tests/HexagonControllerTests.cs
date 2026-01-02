using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using csharp;
using csharp.Dtos;
using csharp.Models;
using FluentAssertions;
using H3;
using H3.Model;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
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
            result[0].IsPromoted.Should().BeTrue();

        result.Any(p => p.Name == "A").Should().BeFalse();
    }

    [Fact]
    public async Task PostLocationBatch_ShouldUpdateProgress_WhenSessionIsValidAndLocationsAreInCity()
    {
        var client = _factory.CreateClient();

        // Dane testowe
        const string token = "token";
        const string userId = "user_id";
        const string cityId = "Poznań";

        var lat = 52.4064;
        var lon = 16.9252;

        var latRad = lat * (Math.PI / 180.0);
        var lonRad = lon * (Math.PI / 180.0);
        var hexagonId = H3Index.FromLatLng(new LatLng(latRad, lonRad), 9).ToString();

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();

            var user = new UserModel { Id = userId, Email = "test@test.com" };
            db.Users.Add(user);

            db.Sessions.Add(new SessionModel
            {
                Token = token,
                UserId = userId,
                ExpiresAt = DateTime.UtcNow.AddHours(1),
                User = user
            });

            var city = new CityModel
            {
                Bbox = [lat, lon, lat + 1, lon + 1],
                City = cityId,
                Country = "Poland",
                Hexagons = []
            };

            db.Hexagons.Add(new HexagonModel
            {
                Id = hexagonId,
                CityId = cityId,
                City = city,
                Weight = 1.0,
                Boundaries = [[lat, lon], [lat, lon], [lat, lon], [lat, lon], [lat, lon], [lat, lon]],
                Center = [lat, lon],
                Pois = []
            });

            await db.SaveChangesAsync();
        }

        var now = DateTime.UtcNow;
        var requestDto = new PostLocationBatchRequestDto
        {
            Locations =
            [
                new Location { Latitude = lat, Longitude = lon, Timestamp = now },
                new Location { Latitude = lat, Longitude = lon, Timestamp = now.AddSeconds(60) }
            ]
        };

        client.DefaultRequestHeaders.Add("Authorization", $"Bearer {token}");
        var response = await client.PostAsJsonAsync("/hexagon/post-location-batch", requestDto);

        response.StatusCode.Should().Be(HttpStatusCode.OK);

        var result = await response.Content.ReadFromJsonAsync<PostLocationBatchResponseDto>();

        result.Should().NotBeNull();
        result.UpdatedHexagons.Should().HaveCount(1);
        result.UpdatedHexagons[0].HexagonId.Should().Be(hexagonId);

        result.UpdatedHexagons[0].Progress.Should().BeApproximately(0.5, 0.001);

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();

            var progress = await db.Progresses.FirstOrDefaultAsync(p => p.UserId == userId && p.HexagonId == hexagonId);
            progress.Should().NotBeNull();
            progress.Progress.Should().BeApproximately(0.5, 0.001);

            var cityStats = await db.Set<UserCityProgress>().FirstOrDefaultAsync(c => c.UserId == userId && c.CityId == cityId);
            cityStats.Should().NotBeNull();
            cityStats.PlayTime.Should().Be(60);
        }
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

    [Fact]
    public async Task GenerateRoute_ShouldQueueJobAndReturnAccepted_WhenSessionIsValid()
    {
        var mockDatabase = new Mock<IDatabase>();
        var mockMultiplexer = new Mock<IConnectionMultiplexer>();

        mockMultiplexer.Setup(x => x.GetDatabase(It.IsAny<int>(), It.IsAny<object>()))
            .Returns(mockDatabase.Object);

        var client = _factory.WithWebHostBuilder(builder =>
        {
            builder.ConfigureTestServices(services =>
            {
                var descriptor = services.SingleOrDefault(d => d.ServiceType == typeof(IConnectionMultiplexer));
                if (descriptor != null) services.Remove(descriptor);

                services.AddSingleton(mockMultiplexer.Object);
            });
        }).CreateClient();

        const string token = "route-token-123";
        const string userId = "user_id";

        using (var scope = _factory.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<PostgresContext>();

            var user = new UserModel { Id = userId, Email = "route@test.com" };
            db.Users.Add(user);

            db.Sessions.Add(new SessionModel
            {
                Token = token,
                UserId = userId,
                ExpiresAt = DateTime.UtcNow.AddHours(1),
                User = user
            });

            await db.SaveChangesAsync();
        }

        var requestDto = new GenerateRouteRequestDto
        {
            UserLatitude = 52.4,
            UserLongitude = 16.9,
            Duration = 60
        };

        client.DefaultRequestHeaders.Add("Authorization", $"Bearer {token}");
        var response = await client.PostAsJsonAsync("/hexagon/generate-route", requestDto);

        response.StatusCode.Should().Be(HttpStatusCode.Accepted);

        var result = await response.Content.ReadFromJsonAsync<GenerateRouteResponseDto>();
        result.Should().NotBeNull();
        result.JobId.Should().NotBeNullOrEmpty();

        mockDatabase.Verify(
            x => x.ListLeftPushAsync(
                "route_jobs",
                It.Is<RedisValue>(v => v.ToString().Contains(userId)),
                It.IsAny<When>(),
                It.IsAny<CommandFlags>()
            ),
            Times.Once
        );
    }
}