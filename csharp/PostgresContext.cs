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
    public DbSet<VersionModel> Versions { get; set; }
    public DbSet<UserHexagonProgress> Progresses { get; set; }

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        var nullableDoubleListConverter = new ValueConverter<List<double>?, string?>(
            v => v == null ? null : JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => v == null ? null : JsonSerializer.Deserialize<List<double>>(v, (JsonSerializerOptions?)null)
        );

        var nullableDoubleListListConverter = new ValueConverter<List<List<double>>?, string?>(
            v => v == null ? null : JsonSerializer.Serialize(v, (JsonSerializerOptions?)null),
            v => v == null ? null : JsonSerializer.Deserialize<List<List<double>>>(v, (JsonSerializerOptions?)null)
        );

        var requiredDoubleListListConverter = new ValueConverter<List<List<double>>, string>(
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
            entity.Property(x => x.Location).HasConversion(nullableDoubleListConverter);
            entity.Property(x => x.Boundary).HasConversion(nullableDoubleListListConverter);

            entity.HasOne(p => p.TouristHexagon)
                .WithMany(h => h.Pois)
                .HasForeignKey(p => p.HexagonId);

            entity.HasOne(p => p.City)
                .WithMany()
                .HasForeignKey(p => p.CityId)
                .OnDelete(DeleteBehavior.Cascade);
        });

        modelBuilder.Entity<HexagonModel>(entity =>
        {
            entity.Property(x => x.Boundaries).HasConversion(requiredDoubleListListConverter);

            entity.Property(x => x.Center).HasConversion(requiredDoubleListConverter);

            entity.HasMany(x => x.Pois)
                .WithOne(p => p.TouristHexagon)
                .HasForeignKey(p => p.HexagonId)
                .OnDelete(DeleteBehavior.Cascade);
        });

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
}