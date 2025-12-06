using System.Text.Json;
using csharp.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;

namespace csharp;

public class PostgresContext(DbContextOptions<PostgresContext> options) : DbContext(options)
{
    // Datasets
    public DbSet<HexagonModel> Hexagons { get; set; }
    public DbSet<PoiModel> Pois { get; set; }
    public DbSet<CityModel> Cities { get; set; }
    public DbSet<UserModel> Users { get; set; }
    public DbSet<SessionModel> Sessions { get; set; }
    public DbSet<VersionModel> Versions { get; set; }
    public DbSet<UserHexagonProgress> Progresses { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        // User & Session Configuration
        modelBuilder.Entity<UserModel>()
            .HasOne(u => u.ActiveSession)
            .WithOne(s => s.User)
            .HasForeignKey<SessionModel>(s => s.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        // City Configuration
        modelBuilder.Entity<CityModel>(entity =>
        {
            entity.Property(x => x.Bbox)
                .HasConversion(JsonConverters.RequiredDoubleList);

            entity.HasMany(c => c.Hexagons)
                .WithOne(h => h.City)
                .HasForeignKey(h => h.CityId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // Hexagon Configuration
        modelBuilder.Entity<HexagonModel>(entity =>
        {
            entity.Property(x => x.Boundaries)
                .HasConversion(JsonConverters.RequiredDoubleListList);

            entity.Property(x => x.Center)
                .HasConversion(JsonConverters.RequiredDoubleList);

            entity.HasMany(x => x.Pois)
                .WithOne(p => p.TouristHexagon)
                .HasForeignKey(p => p.HexagonId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // POI Configuration
        modelBuilder.Entity<PoiModel>(entity =>
        {
            entity.Property(x => x.Location)
                .HasConversion(JsonConverters.NullableDoubleList);

            entity.Property(x => x.Boundary)
                .HasConversion(JsonConverters.NullableDoubleListList);

            entity.HasOne(p => p.TouristHexagon)
                .WithMany(h => h.Pois)
                .HasForeignKey(p => p.HexagonId);

            entity.HasOne(p => p.City)
                .WithMany() // Uni-directional relationship
                .HasForeignKey(p => p.CityId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        // User Progress Configuration
        modelBuilder.Entity<UserHexagonProgress>(entity =>
        {
            entity.HasKey(uh => new { uh.UserId, uh.HexagonId });

            entity.HasOne(uh => uh.User)
                .WithMany(u => u.HexagonProgresses)
                .HasForeignKey(uh => uh.UserId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasOne(uh => uh.Hexagon)
                .WithMany()
                .HasForeignKey(uh => uh.HexagonId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }

    // Helper class to encapsulate JSON serialization logic for EF Core
    private static class JsonConverters
    {
        private static readonly JsonSerializerOptions? Options = null;

        public static readonly ValueConverter<List<double>?, string?> NullableDoubleList = new(
            v => v == null ? null : JsonSerializer.Serialize(v, Options),
            v => v == null ? null : JsonSerializer.Deserialize<List<double>>(v, Options)
        );

        public static readonly ValueConverter<List<List<double>>?, string?> NullableDoubleListList = new(
            v => v == null ? null : JsonSerializer.Serialize(v, Options),
            v => v == null ? null : JsonSerializer.Deserialize<List<List<double>>>(v, Options)
        );

        public static readonly ValueConverter<List<double>, string> RequiredDoubleList = new(
            v => JsonSerializer.Serialize(v, Options),
            v => string.IsNullOrEmpty(v)
                ? new List<double>()
                : JsonSerializer.Deserialize<List<double>>(v, Options) ?? new List<double>()
        );

        public static readonly ValueConverter<List<List<double>>, string> RequiredDoubleListList = new(
            v => JsonSerializer.Serialize(v, Options),
            v => string.IsNullOrEmpty(v)
                ? new List<List<double>>()
                : JsonSerializer.Deserialize<List<List<double>>>(v, Options) ?? new List<List<double>>()
        );
    }
}