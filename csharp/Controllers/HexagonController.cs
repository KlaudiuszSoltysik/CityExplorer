using csharp.Dtos;
using csharp.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using H3;
using H3.Model;
using Microsoft.Extensions.Logging;

namespace csharp.Controllers;

[Route("hexagon")]
[ApiController]
public class HexagonController(PostgresContext postgresContext) : ControllerBase
{
    // Fetch available locations directly from Cities table (Optimized)
    [HttpGet("get-countries-with-cities")]
    public async Task<IActionResult> GetCountriesWithCities()
    {
        var countriesData = await postgresContext.Cities
            .AsNoTracking()
            .GroupBy(c => c.Country)
            .Select(g => new GetCountriesWithCitiesDto
            {
                Country = g.Key,
                Cities = g.Select(c => c.City)
                    .OrderBy(n => n)
                    .ToList()
            })
            .OrderBy(d => d.Country)
            .ToListAsync();

        return Ok(countriesData);
    }

    // Fetch full hexagon data for a specific city
    [HttpGet("get-hexagons-from-city")]
    public async Task<IActionResult> GetHexagonsFromCity([FromQuery] string city)
    {
        var hexagonsData = await postgresContext.Hexagons
            .AsNoTracking()
            .Include(h => h.City)
            .Where(h => h.City.City == city)
            .ToListAsync();

        if (hexagonsData.Count == 0) return NotFound("City or hexagons data not found.");

        var cityBbox = hexagonsData[0].City.Bbox;

        var resultDto = new GetCityHexagonsDataDto
        {
            Bbox = cityBbox,
            Hexagons = hexagonsData.Select(h => new HexagonsDto
            {
                Id = h.Id,
                Boundaries = h.Boundaries,
                Center = h.Center,
                Weight = h.Weight
            }).ToList()
        };

        return Ok(resultDto);
    }

    // Fetch randomized/promoted POIs for a specific hexagon
    [HttpGet("get-pois-from-hexagon")]
    public async Task<IActionResult> GetPoisFromHexagon([FromQuery] string hexagonId)
    {
        var poisData = await postgresContext.Pois
            .AsNoTracking()
            .Where(p => p.HexagonId == hexagonId)
            .Where(p => p.Name != null && p.Name.Length >= 2)
            .OrderByDescending(p => p.IsPromoted)
            .ThenBy(r => EF.Functions.Random())
            .Take(3)
            .Select(p => new GetPoisFromHexagonDto
            {
                Name = p.Name ?? string.Empty,
                Type = p.PoiType,
                IsPromoted = p.IsPromoted
            })
            .ToListAsync();

        return Ok(poisData);
    }

    // Saves batch locations to database and returns progress
    [HttpPost("post-location-batch")]
    public async Task<IActionResult> PostLocationBatch([FromBody] PostLocationBatchDto postLocationBatchDto)
    {
       const int h3Res = 9;

        if (postLocationBatchDto.Locations.Count == 0)
        {
            return BadRequest("No locations provided.");
        }

        var session = await postgresContext.Sessions
            .Include(s => s.User)
            .FirstOrDefaultAsync(s => s.Token == postLocationBatchDto.Token);

        if (session == null || session.ExpiresAt < DateTime.UtcNow)
        {
            return Unauthorized("Invalid or expired token.");
        }

        var sortedLocationsWithHexagonId = postLocationBatchDto.Locations
            .OrderBy(l => l.Timestamp)
            .Select(l =>
            {
                var latitudeRad = l.Latitude * (Math.PI / 180.0);
                var longitudeRad = l.Longitude * (Math.PI / 180.0);
                var hexagonId = H3Index.FromLatLng(new LatLng(latitudeRad, longitudeRad), h3Res).ToString();

                return new { LocationObject = l, HexagonId = hexagonId };
            })
            .ToList();

        var uniqueHexagonIds = sortedLocationsWithHexagonId
            .Select(x => x.HexagonId)
            .Distinct()
            .ToList();

        var hexagonDataMap = await postgresContext.Hexagons
            .AsNoTracking()
            .Where(h => uniqueHexagonIds.Contains(h.Id))
            .Select(h => new { h.Id, h.CityId, h.Weight })
            .ToDictionaryAsync(x => x.Id, x => new { x.CityId, x.Weight });

        var locationsInSupportedArea = sortedLocationsWithHexagonId
            .Where(x => hexagonDataMap.ContainsKey(x.HexagonId))
            .ToList();

        if (locationsInSupportedArea.Count == 0)
        {
            return Ok(new { updatedHexagons = new List<HexagonProgressDto>() });
        }

        var firstLocationHexId = locationsInSupportedArea[0].HexagonId;
        var targetCityId = hexagonDataMap[firstLocationHexId].CityId;

        var finalLocationsToProcess = locationsInSupportedArea
            .Where(x => hexagonDataMap[x.HexagonId].CityId == targetCityId)
            .ToList();

        var totalHexagonsInCity = await postgresContext.Hexagons
            .CountAsync(h => h.CityId == targetCityId);

        if (totalHexagonsInCity == 0) return BadRequest("City appears to be empty.");

        var secondsToComplete = 60.0 * totalHexagonsInCity;

        var hexagonDurationMap = new Dictionary<string, double>();

        for (var i = 0; i < finalLocationsToProcess.Count - 1; i++)
        {
            var current = finalLocationsToProcess[i];
            var next = finalLocationsToProcess[i + 1];

            var duration = (next.LocationObject.Timestamp - current.LocationObject.Timestamp).TotalSeconds;

            hexagonDurationMap.TryAdd(current.HexagonId, 0);
            hexagonDurationMap[current.HexagonId] += duration;
        }

        var affectedHexagonIds = hexagonDurationMap.Keys.ToList();
        var userId = session.UserId;

        var existingProgresses = await postgresContext.Progresses
            .Where(u => u.UserId == userId && affectedHexagonIds.Contains(u.HexagonId))
            .ToListAsync();

        var changesToReturn = new List<HexagonProgressDto>();

        foreach (var hexagonId in affectedHexagonIds)
        {
            var secondsSpent = hexagonDurationMap[hexagonId];

            var weight = hexagonDataMap[hexagonId].Weight > 0 ? hexagonDataMap[hexagonId].Weight : 1.0;

            var progressGained = secondsSpent / (secondsToComplete * weight);

            var record = existingProgresses.FirstOrDefault(x => x.HexagonId == hexagonId);

            if (record != null)
            {
                record.Progress += progressGained;
                if (record.Progress > 1.0) record.Progress = 1.0;
            }
            else
            {
                record = new UserHexagonProgress
                {
                    UserId = userId,
                    HexagonId = hexagonId,
                    Progress = Math.Min(progressGained, 1.0)
                };
                postgresContext.Progresses.Add(record);
            }

            changesToReturn.Add(new HexagonProgressDto
            {
                HexagonId = hexagonId,
                Progress = record.Progress
            });
        }

        await postgresContext.SaveChangesAsync();

        return Ok(new
        {
            updatedHexagons = changesToReturn,
        });
    }

    // Fetch all user progresses for a city
    [HttpGet("get-hexagon-progresses")]
    public async Task<IActionResult> GetHexagonProgresses([FromBody] string token, [FromQuery] string city)
    {
        var session = await postgresContext.Sessions
            .AsNoTracking()
            .Include(s => s.User)
            .FirstOrDefaultAsync(s => s.Token == token);

        if (session == null || session.ExpiresAt < DateTime.UtcNow)
            return Unauthorized("Invalid token.");

        var userModel = session.User;

        var validHexagonIds = await postgresContext.Hexagons
            .Where(h => h.City.City == city)
            .Select(h=>h.Id)
            .ToListAsync();

        var progresses =
            postgresContext.Progresses
                .Where(p => p.UserId == userModel.Id && validHexagonIds.Contains(p.HexagonId))
                .Select(p => new HexagonProgressDto
                {
                    HexagonId = p.HexagonId.ToString(),
                    Progress = p.Progress
                })
                .ToListAsync();

        return Ok(progresses);
    }
}