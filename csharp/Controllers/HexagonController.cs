using csharp.Dtos;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace csharp.Controllers;

[Route("hexagon")]
[ApiController]
public class HexagonController(PostgresContext postgresContext) : ControllerBase
{
    [HttpGet("get-countries-with-cities")]
    public async Task<IActionResult> GetCountriesWithCities()
    {
        var countriesData = await postgresContext.Hexagons
            .GroupBy(h => h.City.Country)
            .Select(g => new GetCountriesWithCitiesDto
            {
                Country = g.Key,
                Cities = g.Select(h => h.City.City)
                    .Distinct()
                    .OrderBy(c => c)
                    .ToList()
            })
            .OrderBy(c => c.Country)
            .ToListAsync();

        return Ok(countriesData);
    }

    [HttpGet("get-hexagons-from-city")]
    public async Task<IActionResult> GetHexagonsFromCity([FromQuery] string city)
    {
        var hexagonsData = await postgresContext.Hexagons
            .Include(h => h.City)
            .Where(h => h.City.City == city)
            .ToListAsync();

        if (hexagonsData.Count == 0) return NotFound("City or hexagons data not found.");

        var cityBbox = hexagonsData.First().City.Bbox;

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

    [HttpGet("get-pois-from-hexagon")]
    public async Task<IActionResult> GetPoisFromHexagon([FromQuery] string hexagonId)
    {
        var poisData = await postgresContext.Pois
            .Where(p => p.HexagonId == hexagonId)
            .Where(p => p.Name != null && p.Name.Length >= 2)
            .OrderByDescending(p => p.IsPromoted)
            .ThenBy(r => EF.Functions.Random())
            .Select(p => new GetPoisFromHexagonDto
            {
                Name = p.Name ?? "",
                Type = p.PoiType,
                IsPromoted = p.IsPromoted
            })
            .Take(3)
            .ToListAsync();

        return Ok(poisData);
    }
}