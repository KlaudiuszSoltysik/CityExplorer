using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace csharp.Controllers;

[Route("version")]
[ApiController]
public class VersionController(PostgresContext postgresContext) : ControllerBase
{
    [HttpGet("get-current-version")]
    public async Task<IActionResult> GetCurrentVersion([FromQuery] string key)
    {
        var version = await postgresContext.Versions.FirstOrDefaultAsync(x => x.Key == key);

        if (version == null) return NotFound();

        return Ok(version.VersionNumber);
    }
}