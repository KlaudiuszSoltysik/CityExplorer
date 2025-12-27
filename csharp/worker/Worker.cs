using System.Text.Json;
using StackExchange.Redis;

namespace worker;

public class Worker(ILogger<Worker> logger, IConnectionMultiplexer redis) : BackgroundService
{
    private const string QueueName = "route_jobs";

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        var db = redis.GetDatabase();
        const int blockSeconds = 3;

        logger.LogInformation("Worker started: {QueueName}", QueueName);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                var result = await db.ExecuteAsync("BRPOP", QueueName, blockSeconds);

                if (result.IsNull) continue;

                var resultArray = (RedisResult[])result!;
                var jobJson = (string)resultArray[1]!;

                logger.LogInformation("Job received: {Job}", jobJson);

                try
                {
                    using var doc = JsonDocument.Parse(jobJson);

                    var root = doc.RootElement;
                    var jobId = root.GetProperty("JobId").GetString();

                    logger.LogInformation("Processing JobId: {JobId}...", jobId);

                    // Symulacja pracy (np. algorytm mrówkowy)
                    await Task.Delay(2000, stoppingToken);

                    logger.LogInformation("Completed JobId: {JobId}", jobId);
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
}