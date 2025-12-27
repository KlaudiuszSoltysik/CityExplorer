using H3;
using H3.Algorithms;
using H3.Extensions;

namespace worker;

public interface IH3Service
{
    List<string> GetKRing(string originHexId, int k);

    List<string> GetNeighbors(string originHexId);

    int GetDistance(string hexA, string hexB);
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
}