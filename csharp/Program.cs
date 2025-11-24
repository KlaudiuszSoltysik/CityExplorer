using csharp;
using Microsoft.EntityFrameworkCore;
using Scalar.AspNetCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddOpenApi();
builder.Services.AddControllers();
builder.Services.AddDbContext<PostgresContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("PostgresConnection")));
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.SetIsOriginAllowed(_ => true)
            .AllowAnyHeader()
            .AllowAnyMethod()
            .AllowCredentials();
    });
});

// builder.WebHost.UseUrls("http://localhost:6101");
builder.WebHost.UseUrls("http://0.0.0.0:6101");

var app = builder.Build();

app.UseHttpsRedirection();
app.MapOpenApi();
app.MapScalarApiReference();
app.UseCors();
app.MapControllers();


app.Run();