using csharp.Dtos;
using csharp.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace csharp.Controllers;

[Route("hexagon")]
[ApiController]
public class HexagonController(PostgresContext postgresContext) : ControllerBase
{
    [HttpGet("get-hexagons-from-city")]
    public async Task<IActionResult> GetHexagonsFromCity([FromQuery] string mode, [FromQuery] string city)
    {
        if (mode != "tourist" && mode != "local")
        {
            return BadRequest("Invalid mode specified. Must be 'tourist' or 'local'.");
        }

        var hexagonsData = await postgresContext.Hexagons
            .Where(h => h.City == city)
            .Select(h => new GetHexagonsFromCityDto
            {
                Id = h.Id,
                Weight = mode == "tourist" ? h.TouristWeight : h.LocalWeight
            })
            .ToListAsync();

        return Ok(hexagonsData);
    }
}