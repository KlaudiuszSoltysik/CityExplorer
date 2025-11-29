using System.Text.Json;
using csharp.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage.ValueConversion;

namespace csharp;

public class PostgresContext(DbContextOptions<PostgresContext> options) : DbContext(options)
{
    public DbSet<HexagonModel> Hexagons { get; set; }
    public DbSet<PoiModel> Pois { get; set; }
    public DbSet<CityModel> Cities { get; set; }
    public DbSet<UserModel> Users { get; set; }
    public DbSet<SessionModel> Sessions { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        var doubleListConverter = new ValueConverter<List<double>?, string?>(
            v => v == null ? null : JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => v == null ? null : JsonSerializer.Deserialize<List<double>>(v, (JsonSerializerOptions?)null)
        );

        var nullableDoubleListListConverter = new ValueConverter<List<List<double>>?, string?>(
            v => v == null ? null : JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => v == null ? null : JsonSerializer.Deserialize<List<List<double>>>(v, (JsonSerializerOptions?)null)
        );

        var doubleListListConverter = new ValueConverter<List<List<double>>, string>(
            v => JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => string.IsNullOrEmpty(v)
                ? new List<List<double>>()
                : JsonSerializer.Deserialize<List<List<double>>>(v, (JsonSerializerOptions?)null) ??
                  new List<List<double>>()
        );

        var requiredDoubleListConverter = new ValueConverter<List<double>, string>(
            v => JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => string.IsNullOrEmpty(v)
                ? new List<double>()
                : JsonSerializer.Deserialize<List<double>>(v, (JsonSerializerOptions?)null) ?? new List<double>()
        );

        modelBuilder.Entity<UserModel>()
            .HasOne(u => u.ActiveSession)
            .WithOne(s => s.User)
            .HasForeignKey<SessionModel>(s => s.UserId)
            .OnDelete(DeleteBehavior.Cascade);

        modelBuilder.Entity<CityModel>(entity =>
        {
            entity.Property(x => x.Bbox).HasConversion(requiredDoubleListConverter);

            entity.HasMany(c => c.Hexagons)
                .WithOne(h => h.City)
                .HasForeignKey(h => h.CityId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<PoiModel>(entity =>
        {
            entity.Property(x => x.Location).HasConversion(doubleListConverter);
            entity.Property(x => x.Boundary).HasConversion(nullableDoubleListListConverter);
        });

        modelBuilder.Entity<HexagonModel>(entity =>
        {
            entity.Property(x => x.Boundaries).HasConversion(doubleListListConverter);

            entity.HasMany(x => x.TouristPois)
                .WithOne(p => p.TouristHexagon)
                .HasForeignKey(p => p.TouristHexagonId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasMany(x => x.LocalPois)
                .WithOne(p => p.LocalHexagon)
                .HasForeignKey(p => p.LocalHexagonId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }
}