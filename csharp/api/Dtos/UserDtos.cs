using System.Text.Json.Serialization;

namespace csharp.Dtos;

public class ValidateLoginTokenRequestDto
{
    [JsonPropertyName("googleToken")] public required string GoogleToken { get; init; }
}

public class ValidateLoginTokenResponseDto
{
    [JsonPropertyName("isSuccess")] public bool IsSuccess { get; init; }

    [JsonPropertyName("token")] public required string Token { get; init; }
}

public class ValidateAuthorizationTokenResponseDto
{
    [JsonPropertyName("id")] public string Id { get; init; } = string.Empty;
}

public class GetUserStatisticsResponseDto
{
    [JsonPropertyName("explored")] public double Explored { get; init; }

    [JsonPropertyName("progress")] public int Progress { get; init; }

    [JsonPropertyName("hexagonCount")] public int HexagonCount { get; init; }

    [JsonPropertyName("playTime")] public int PlayTime { get; init; }

    [JsonPropertyName("distance")] public int Distance { get; init; }

    [JsonPropertyName("ranking")] public int Ranking { get; init; }

    [JsonPropertyName("userCount")] public int UserCount { get; init; }
}