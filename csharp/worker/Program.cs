using StackExchange.Redis;
using worker;

var builder = Host.CreateApplicationBuilder(args);
builder.Services.AddHostedService<Worker>();

var redisConnection = ConnectionMultiplexer.Connect(builder.Configuration.GetConnectionString("RedisConnection") ?? "redis-dev:6379");
builder.Services.AddSingleton<IConnectionMultiplexer>(redisConnection);

var host = builder.Build();
host.Run();