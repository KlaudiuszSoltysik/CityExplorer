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

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        var doubleListConverter = new ValueConverter<List<double>?, string?>(
            v => v == null ? null : JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => v == null ? null : JsonSerializer.Deserialize<List<double>>(v, (JsonSerializerOptions?)null)
        );

        var doubleListListConverter = new ValueConverter<List<List<double>>?, string?>(
            v => v == null ? null : JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => v == null ? null : JsonSerializer.Deserialize<List<List<double>>>(v, (JsonSerializerOptions?)null)
        );

        var doubleListListConverter2 = new ValueConverter<List<List<double>>, string>(
            v => JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => JsonSerializer.Deserialize<List<List<double>>>(v, (JsonSerializerOptions?)null) ??
                 new List<List<double>>()
        );

        var doubleListConverter2 = new ValueConverter<List<double>, string>(
            v => JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => JsonSerializer.Deserialize<List<double>>(v, (JsonSerializerOptions?)null) ??
                 new List<double>()
        );

        modelBuilder.Entity<CityModel>(entity =>
        {
            entity.HasKey(x => x.City);

            entity.Property(x => x.Bbox)
                .HasConversion(doubleListConverter2);
        });

        modelBuilder.Entity<PoiModel>(entity =>
        {
            entity.HasKey(x => x.Id);

            entity.Property(x => x.Location)
                .HasConversion(doubleListConverter);

            entity.Property(x => x.Boundary)
                .HasConversion(doubleListListConverter);
        });

        modelBuilder.Entity<HexagonModel>(entity =>
        {
            entity.HasKey(x => x.Id);

            entity.Property(x => x.Boundaries)
                .HasConversion(doubleListListConverter2);

            entity
                .HasMany(x => x.TouristPois)
                .WithOne()
                .HasForeignKey("TouristHexagonId")
                .OnDelete(DeleteBehavior.Cascade);

            entity
                .HasMany(x => x.LocalPois)
                .WithOne()
                .HasForeignKey("LocalHexagonId")
                .OnDelete(DeleteBehavior.Cascade);

            entity
                .HasOne(x => x.City)
                .WithMany()
                .HasForeignKey(x => x.CityId)
                .OnDelete(DeleteBehavior.Cascade);
        });
    }
}