using csharp.Dtos;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

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
}