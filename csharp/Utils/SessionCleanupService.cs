using Microsoft.EntityFrameworkCore;

namespace csharp.Utils;

public class SessionCleanupService(IServiceProvider serviceProvider)
    : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
            try
            {
                using (var scope = serviceProvider.CreateScope())
                {
                    var dbContext = scope.ServiceProvider.GetRequiredService<PostgresContext>();

                    await dbContext.Sessions
                        .Where(s => s.ExpiresAt < DateTime.UtcNow)
                        .ExecuteDeleteAsync(stoppingToken);
                }

                await Task.Delay(TimeSpan.FromHours(24), stoppingToken);
            }
            catch (Exception)
            {
                await Task.Delay(TimeSpan.FromMinutes(5), stoppingToken);
            }
    }
}