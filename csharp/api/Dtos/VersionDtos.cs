using System.Text.Json.Serialization;

namespace csharp.Dtos;

public class GetCurrentVersionRequestDto
{
    [JsonPropertyName("key")] public string Key { get; init; } = string.Empty;
}

public class GetCurrentVersionResponseDto
{
    [JsonPropertyName("version")] public string Version { get; init; } = string.Empty;
}