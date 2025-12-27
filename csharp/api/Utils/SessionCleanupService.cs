using Microsoft.EntityFrameworkCore;

namespace csharp.Utils;

public class SessionCleanupService(
    IServiceProvider serviceProvider,
    ILogger<SessionCleanupService> logger) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        logger.LogInformation("Session Cleanup Service started.");

        while (!stoppingToken.IsCancellationRequested)
            try
            {
                using var scope = serviceProvider.CreateScope();
                var dbContext = scope.ServiceProvider.GetRequiredService<PostgresContext>();

                var deletedCount = await dbContext.Sessions
                    .Where(s => s.ExpiresAt < DateTime.UtcNow)
                    .ExecuteDeleteAsync(stoppingToken);

                if (deletedCount > 0) logger.LogInformation("Cleaned up {Count} expired sessions.", deletedCount);

                await Task.Delay(TimeSpan.FromHours(24), stoppingToken);
            }
            catch (Exception ex)
            {
                logger.LogError(ex, "Error occurred during session cleanup.");
                await Task.Delay(TimeSpan.FromMinutes(5), stoppingToken);
            }
    }
}