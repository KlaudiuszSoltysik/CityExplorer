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
    public async Task<IActionResult> GetHexagonsFromCity([FromQuery] string mode, [FromQuery] string city)
    {
        if (mode != "tourist" && mode != "local")
            return BadRequest("Invalid mode specified. Must be 'tourist' or 'local'.");

        var hexagonsData = await postgresContext.Hexagons
            .Include(h => h.City)
            .Where(h => h.City.City == city)
            .ToListAsync();

        if (hexagonsData.Count == 0)
        {
            return NotFound("City or hexagons data not found.");
        }

        var cityBbox = hexagonsData.First().City.Bbox;

        var resultDto = new GetCityHexagonsDataDto
        {
            Bbox = cityBbox,
            Hexagons = hexagonsData.Select(h => new HexagonsDto
            {
                Id = h.Id,
                Boundaries = h.Boundaries,
                Weight = mode == "tourist" ? h.TouristWeight : h.LocalWeight
            }).ToList()
        };

        return Ok(resultDto);
    }
}