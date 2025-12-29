using csharp.Dtos;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace csharp.Controllers;

[Route("version")]
[ApiController]
public class VersionController(PostgresContext postgresContext) : ControllerBase
{
    // Retrieve current data version by key (used for caching invalidation)
    [HttpGet("get-current-version")]
    public async Task<IActionResult> GetCurrentVersion([FromQuery] GetCurrentVersionRequestDto requestDto)
    {
        var version = await postgresContext.Versions
            .AsNoTracking()
            .FirstOrDefaultAsync(x => x.Key == requestDto.Key);

        var responseDto = new GetCurrentVersionResponseDto
        {
            Version = version == null ? "0" : version.VersionNumber
        };

        return Ok(responseDto);
    }
}