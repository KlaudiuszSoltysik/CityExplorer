using System.Text.Json;
using csharp.Dtos;
using Microsoft.Extensions.Caching.Distributed;

namespace csharp.Utils;

public interface IJobStateService
{
    Task SaveResultAsync(string jobId, WorkerResult result);
    Task<WorkerResult?> GetResultAsync(string jobId);
}

public class JobStateService(IDistributedCache cache) : IJobStateService
{
    public async Task SaveResultAsync(string jobId, WorkerResult result)
    {
        var json = JsonSerializer.Serialize(result);

        await cache.SetStringAsync(jobId, json, new DistributedCacheEntryOptions
        {
            AbsoluteExpirationRelativeToNow = TimeSpan.FromMinutes(1)
        });
    }

    public async Task<WorkerResult?> GetResultAsync(string jobId)
    {
        var json = await cache.GetStringAsync(jobId);

        return string.IsNullOrEmpty(json) ? null : JsonSerializer.Deserialize<WorkerResult>(json);
    }
}