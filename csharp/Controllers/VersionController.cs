using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace csharp.Controllers;

[Route("version")]
[ApiController]
public class VersionController(PostgresContext postgresContext) : ControllerBase
{
    // Retrieve current data version by key (used for caching invalidation)
    [HttpGet("get-current-version")]
    public async Task<IActionResult> GetCurrentVersion([FromQuery] string key)
    {
        var version = await postgresContext.Versions
            .AsNoTracking()
            .FirstOrDefaultAsync(x => x.Key == key);

        if (version == null) return NotFound();

        return Ok(version.VersionNumber);
    }
}