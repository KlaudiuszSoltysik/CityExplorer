using csharp.Dtos;
using csharp.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using H3;
using H3.Model;

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
    public async Task<IActionResult> PostLocationBatch([FromBody] PostLocationBatchDto locationsDto)
    {
        const double secondsToComplete = 600.0;
        const int h3Res = 9;

        var session = await postgresContext.Sessions
            .Include(s => s.User)
            .FirstOrDefaultAsync(s => s.Token == locationsDto.Token);

        if (session == null || session.ExpiresAt < DateTime.UtcNow)
        {
            return Unauthorized("Invalid or expired token.");
        }

        var userId = session.UserId;

        var sortedLocations = locationsDto.Locations
            .OrderBy(l => l.Timestamp)
            .ToList();

        var hexDurationMap = new Dictionary<string, double>();

        for (var i = 0; i < sortedLocations.Count - 1; i++)
        {
            var current = sortedLocations[i];
            var next = sortedLocations[i + 1];

            var duration = (next.Timestamp - current.Timestamp).TotalSeconds;

            switch (duration)
            {
                case > 120:
                case < 0:
                    continue;
            }

            var latRad = current.Lat * (Math.PI / 180.0);
            var lonRad = current.Lon * (Math.PI / 180.0);

            var hexId = H3Index.FromLatLng(new LatLng(latRad, lonRad), h3Res).ToString();

            hexDurationMap.TryAdd(hexId, 0);
            hexDurationMap[hexId] += duration;
        }

        var affectedHexIds = hexDurationMap.Keys.ToList();

        var existingProgresses = await postgresContext.Progresses
            .Where(u => u.UserId == userId && affectedHexIds
                .Contains(u.HexagonId))
            .ToListAsync();

        var changesToReturn = new List<HexagonUpdateDto>();

        foreach (var hexId in affectedHexIds)
        {
            var secondsSpent = hexDurationMap[hexId];
            var progressGained = secondsSpent / secondsToComplete;

            var record = existingProgresses
                .FirstOrDefault(x => x.HexagonId == hexId);

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
                    HexagonId = hexId,
                    Progress = Math.Min(progressGained, 1.0)
                };
                postgresContext.Progresses
                    .Add(record);
            }

            changesToReturn.Add(new HexagonUpdateDto
            {
                HexagonId = hexId,
                Progress = record.Progress
            });
        }

        await postgresContext.SaveChangesAsync();

        return Ok(new
        {
            updatedHexagons = changesToReturn
        });
    }
}