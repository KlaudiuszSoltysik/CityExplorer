namespace csharp.Utils;

public class ApiKeyMiddleware(RequestDelegate next, IConfiguration configuration)
{
    public async Task InvokeAsync(HttpContext context)
    {
        if (!context.Request.Headers.TryGetValue("X-Api-Key", out var extractedApiKey))
        {
            context.Response.StatusCode = 401;
            await context.Response.WriteAsync("API Key missing");
            return;
        }

        var apiKey = configuration.GetValue<string>("ApiKey");

        if (string.IsNullOrEmpty(apiKey) || apiKey != extractedApiKey)
        {
            context.Response.StatusCode = 403;
            await context.Response.WriteAsync("Invalid API Key");
            return;
        }

        await next(context);
    }
}