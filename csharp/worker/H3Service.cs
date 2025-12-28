using H3;
using H3.Algorithms;
using H3.Extensions;
using H3.Model;

namespace worker;

public interface IH3Service
{
    List<string> GetKRing(string originHexId, int k);

    List<string> GetNeighbors(string originHexId);

    int GetDistance(string hexA, string hexB);

    string GetHexagonId(double latitude, double longitude);
}

public class H3Service : IH3Service
{
    public List<string> GetKRing(string originHexId, int k)
    {
        var origin = new H3Index(originHexId);

        return origin.GridDiskDistances(k)
            .Select(cell => cell.Index.ToString())
            .ToList();
    }

    public List<string> GetNeighbors(string originHexId)
    {
        var origin = new H3Index(originHexId);

        return origin.GetNeighbours().Select(h => h.ToString()).ToList();
    }

    public int GetDistance(string hexA, string hexB)
    {
        var a = new H3Index(hexA);
        var b = new H3Index(hexB);
        return a.GridDistance(b);
    }

    public string GetHexagonId(double latitude, double longitude)
    {
        const int h3Res = 9;

        var latitudeRad = latitude * (Math.PI / 180.0);
        var longitudeRad = longitude * (Math.PI / 180.0);

        return  H3Index.FromLatLng(new LatLng(latitudeRad, longitudeRad), h3Res).ToString();
    }
}