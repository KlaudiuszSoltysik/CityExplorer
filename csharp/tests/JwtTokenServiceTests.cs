using csharp.Utils;
using FluentAssertions;
using Microsoft.Extensions.Configuration;

namespace tests;

public class JwtTokenServiceTests
{
    [Fact]
    public void CreateAppJwtToken_ShouldReturnValidString_WhenDataIsCorrect()
    {
        var inMemoryConfig = new Dictionary<string, string> {
            {"JwtSettings:SecretKey", "jwt_secret_key__________________"},
            {"JwtSettings:Issuer", "my-api"},
            {"JwtSettings:Audience", "my-app"}
        };

        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(inMemoryConfig!)
            .Build();

        var token = JwtTokenService.CreateAppJwtToken("user", "user@gmail.com", config);

        token.Should().NotBeNullOrEmpty();
        token.Split('.').Should().HaveCount(3);
    }

    [Fact]
    public void CreateAppJwtToken_ShouldThrowException_WhenSecretKeyIsMissing()
    {
        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string>()!)
            .Build();

        Action act = () => JwtTokenService.CreateAppJwtToken("id", "email", config);

        act.Should().Throw<InvalidOperationException>()
            .WithMessage("JWT SecretKey missing in config");
    }
}