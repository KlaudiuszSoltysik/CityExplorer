using csharp;
using csharp.Utils;
using Microsoft.EntityFrameworkCore;
using Scalar.AspNetCore;
using StackExchange.Redis;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddOpenApi();

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

builder.Services.AddHostedService<SessionCleanupService>();

builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy
            .AllowAnyOrigin()
            .AllowAnyMethod()
            .AllowAnyHeader();
    });
});

var app = builder.Build();

if (app.Environment.IsDevelopment() || app.Environment.IsEnvironment("Testing"))
{
    app.MapOpenApi();
    app.MapScalarApiReference();
}
else
{
    app.UseMiddleware<ApiKeyMiddleware>();
}

app.UseCors();

app.UseAuthorization();

app.MapControllers();

app.Run();

public partial class Program { }