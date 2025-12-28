using System.Text.Json;
using csharp;
using csharp.Dtos;
using csharp.Utils;
using Microsoft.AspNetCore.SignalR;
using Microsoft.EntityFrameworkCore;
using StackExchange.Redis;

namespace worker;

public class Worker(
    ILogger<Worker> logger,
    IConnectionMultiplexer redis,
    IH3Service h3Service,
    IServiceScopeFactory scopeFactory,
    IHubContext<WorkerHub, IWorkerHub> hubContext,
    IJobStateService jobStateService
) : BackgroundService
{
    private const string QueueName = "route_jobs";
    private const double MinutesPerHexagon = 6.0;

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var db = redis.GetDatabase();
        const int blockSeconds = 3;

        while (!stoppingToken.IsCancellationRequested)
            try
            {
                var result = await db.ExecuteAsync("BRPOP", QueueName, blockSeconds);

                if (result.IsNull) continue;

                var resultArray = (RedisResult[])result!;
                var jobJson = (string)resultArray[1]!;

                try
                {
                    using var doc = JsonDocument.Parse(jobJson);

                    var root = doc.RootElement;

                    var jobId = root.GetProperty("JobId").GetString();
                    var userId = root.GetProperty("UserId").GetString();
                    var userLatitude = root.GetProperty("UserLatitude").GetDouble();
                    var userLongitude = root.GetProperty("UserLongitude").GetDouble();
                    var duration = root.GetProperty("Duration").GetInt32();

                    if (jobId is null || userId is null) continue;

                    var totalStepsBudget = (int)(duration / MinutesPerHexagon);
                    var kRingSize = (int)(duration / 2.0 / MinutesPerHexagon) + 2;

                    if (kRingSize < 3) kRingSize = 3;

                    var hexagonId = h3Service.GetHexagonId(userLatitude, userLongitude);

                    var hexagonIdsInRange = h3Service.GetKRing(hexagonId, kRingSize);

                    List<GraphNode> nodes;

                    using (var scope = scopeFactory.CreateScope())
                    {
                        var dbContext = scope.ServiceProvider.GetRequiredService<PostgresContext>();

                        nodes = await dbContext.Hexagons
                            .AsNoTracking()
                            .Where(h => hexagonIdsInRange.Contains(h.Id))
                            .Select(h => new GraphNode
                            {
                                HexagonId = h.Id,
                                Weight = h.Weight,
                                Progress = dbContext.Progresses
                                    .Where(p => p.HexagonId == h.Id && p.UserId == userId)
                                    .Select(p => p.Progress)
                                    .FirstOrDefault()
                            })
                            .ToListAsync(stoppingToken);
                    }

                    var input = new AcoInput
                    {
                        StartHexagonId = hexagonId,
                        MaxDistance = totalStepsBudget,
                        Nodes = nodes
                    };

                    var aco = new AntColonyOptimizer(h3Service, input);

                    var route = aco.Solve();

                    var workerResult = new WorkerResult
                    {
                        Route = route
                    };

                    await jobStateService.SaveResultAsync(jobId, workerResult);

                    await hubContext.Clients.Group(jobId).JobCompleted(workerResult);
                }
                catch (Exception e)
                {
                    logger.LogError("Error: {Message}", e.Message);
                }
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Connection error. Reconnecting...");
                await Task.Delay(5000, stoppingToken);
            }
    }
}