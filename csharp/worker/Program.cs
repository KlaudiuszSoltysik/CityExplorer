using csharp;
using Microsoft.EntityFrameworkCore;
using StackExchange.Redis;
using worker;

var builder = Host.CreateApplicationBuilder(args);

if (builder.Environment.EnvironmentName != "Testing")
{
    builder.Services.AddDbContext<PostgresContext>(options =>
        options.UseNpgsql(builder.Configuration.GetConnectionString("PostgresConnection")));

    builder.Services.AddSingleton<IConnectionMultiplexer>(sp =>
    {
        var configuration = sp.GetRequiredService<IConfiguration>();
        var redisConnectionString = configuration.GetConnectionString("RedisConnection") ?? "redis-dev:6379";

        try
        {
            var redisOptions = ConfigurationOptions.Parse(redisConnectionString);
            redisOptions.AbortOnConnectFail = false;
            return ConnectionMultiplexer.Connect(redisOptions);
        }
        catch (Exception ex)
        {
            Console.WriteLine($"[Redis Error] {ex.Message}");
            throw;
        }
    });
}

builder.Services.AddSignalR()
    .AddStackExchangeRedis(builder.Configuration.GetConnectionString("RedisConnection") ?? "redis-dev:6379");

builder.Services.AddSingleton<IH3Service, H3Service>();

builder.Services.AddHostedService<Worker>();

var host = builder.Build();
host.Run();