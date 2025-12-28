using System.Collections.Concurrent;

namespace worker;

public class AcoInput
{
    public required string StartHexagonId { get; init; }
    public int MaxDistance { get; init; }
    public required List<GraphNode> Nodes { get; init; }
}

public class GraphNode
{
    public required string HexagonId { get; init; }
    public double Weight { get; init; }
    public double Progress { get; init; }
}

public class AntColonyOptimizer
{
    // Population size per iteration; determines the breadth of the parallel search.
    private const int NumberOfAnts = 20;

    // Total simulation cycles to allow the algorithm to converge on a solution.
    private const int Iterations = 50;

    // Pheromone importance factor. Higher values favor "following the crowd" (exploitation of historical paths).
    private const double Alpha = 1.0;

    // Heuristic importance factor. Higher values favor "greedy" moves towards high-score nodes (immediate reward).
    private const double Beta = 2.5;

    // Pheromone decay coefficient (0.0-1.0). Prevents stagnation in local optima by reducing old trail intensity over time.
    private const double EvaporationRate = 0.1;
    private readonly Dictionary<string, GraphNode> _graph;
    private readonly IH3Service _h3Service;

    private readonly AcoInput _input;

    // Shared memory for pheromone trails on edges. Key is "HexA-HexB".
    private readonly ConcurrentDictionary<string, double> _pheromones = new();

    public AntColonyOptimizer(IH3Service h3Service, AcoInput input)
    {
        _h3Service = h3Service;

        _input = input;

        // Create a dictionary for O(1) lookup performance during graph traversal.
        _graph = input.Nodes.ToDictionary(n => n.HexagonId);
    }

    // Executes the Ant Colony Optimization algorithm to find the maximum reward path.
    public List<string> Solve()
    {
        List<string> globalBestPath = [];
        var globalBestScore = -1.0;

        // Main simulation loop (generations of ants).
        for (var i = 0; i < Iterations; i++)
        {
            // Thread-safe collection to store results from parallel execution.
            var antsPaths = new ConcurrentBag<(List<string> Path, double Score)>();

            // Run ants in parallel to maximize CPU utilization.
            Parallel.For(0, NumberOfAnts, _ =>
            {
                var result = RunAnt();
                if (result.Score > 0) antsPaths.Add(result);
            });

            if (antsPaths.IsEmpty) continue;

            // Find the best solution in the current generation.
            var bestAnt = antsPaths.MaxBy(x => x.Score);

            // Update global best if the current generation found a better path.
            if (bestAnt.Score > globalBestScore)
            {
                globalBestScore = bestAnt.Score;
                globalBestPath = bestAnt.Path;
            }

            // Update pheromone trails based on the results of this iteration.
            UpdatePheromones(antsPaths);
        }

        return globalBestPath;
    }

    // Simulates a single ant constructing a path through the graph.
    private (List<string> Path, double Score) RunAnt()
    {
        var currentHexagon = _input.StartHexagonId;
        var startHexagon = _input.StartHexagonId;

        var path = new List<string> { currentHexagon };
        // Tracks unique visits for scoring purposes (we don't score the same node twice).
        var visitedForScore = new HashSet<string> { currentHexagon };

        var score = 0.0;
        var returnedHome = false;

        // Step limit defines the maximum duration/distance of the walk.
        for (var step = 1; step <= _input.MaxDistance; step++)
        {
            var neighbors = _h3Service.GetNeighbors(currentHexagon);
            var candidates = new List<string>();

            foreach (var neighborId in neighbors)
            {
                // Filter: Node must exist in our data graph.
                if (!_graph.ContainsKey(neighborId)) continue;

                var isHome = neighborId == startHexagon;

                // Special handling for the start node:
                // Only allow returning home if the path has some length (> 2) to prevent immediate A->B->A loops.
                if (isHome)
                {
                    if (path.Count > 2) candidates.Add(neighborId);
                    continue;
                }

                // Calculate if the ant has enough remaining budget to return home after making this move.
                // Cost: Current Step + 1 (move to neighbor) + Distance from neighbor to Start.
                var distHome = _h3Service.GetDistance(neighborId, startHexagon);
                if (step + 1 + distHome <= _input.MaxDistance) candidates.Add(neighborId);
            }

            // Optimization: If multiple choices exist, remove the node we just came from (immediate backtrack).
            // This encourages forward exploration but allows backtracking if it's the only option (dead end).
            if (candidates.Count > 1)
            {
                var prevHex = path.Count > 1 ? path[^2] : null;
                if (prevHex != null && candidates.Contains(prevHex)) candidates.Remove(prevHex);
            }

            // No valid moves left (either dead end or not enough budget to return).
            if (candidates.Count == 0) break;

            // Select next step probabilistically.
            var nextHexagon = SelectNextNode(currentHexagon, candidates, visitedForScore);

            path.Add(nextHexagon);
            currentHexagon = nextHexagon;

            // Check if the ant successfully closed the loop.
            if (currentHexagon == startHexagon)
            {
                returnedHome = true;
                break;
            }

            // If already scored, skip adding points (but the move is valid).
            if (visitedForScore.Contains(currentHexagon)) continue;

            // Accumulate score: Higher weight and lower user progress yield higher rewards.
            var node = _graph[currentHexagon];
            score += node.Weight * (1.0 - node.Progress) + 0.1;
            visitedForScore.Add(currentHexagon);
        }

        // If the ant didn't return to the start, the path is invalid (score = 0).
        return !returnedHome ? (path, 0) : (path, score);
    }

    // Selects the next node using Roulette Wheel Selection based on Pheromones and Heuristics.
    private string SelectNextNode(string currentHexagon, List<string> candidates, HashSet<string> visitedSet)
    {
        var probabilities = new double[candidates.Count];
        double sumProb = 0;

        for (var i = 0; i < candidates.Count; i++)
        {
            var neighborId = candidates[i];
            var node = _graph[neighborId];

            // PHEROMONE: Get existing trail intensity (History).
            var edgeKey = GetEdgeKey(currentHexagon, neighborId);
            var pheromone = _pheromones.GetValueOrDefault(edgeKey, 0.1);

            double heuristic;

            // HEURISTIC: Calculate attractiveness (Greediness).
            if (visitedSet.Contains(neighborId))
                // PENALTY: If already visited, drastically reduce attractiveness.
                // This allows escaping dead ends but discourages loops/backtracking on open paths.
                heuristic = node.Weight * (1.1 - node.Progress) * 0.1;
            else
                // STANDARD: Base weight adjusted by user progress (unvisited areas are more attractive).
                // 1.1 ensures a small base value even if progress is 100% (1.0).
                heuristic = node.Weight * (1.1 - node.Progress);

            // ACO Transition Formula: P = (τ^α) * (η^β)
            var p = Math.Pow(pheromone, Alpha) * Math.Pow(heuristic, Beta);

            probabilities[i] = p;
            sumProb += p;
        }

        // Roulette Wheel Selection: Random sampling from the probability distribution.
        var rand = Random.Shared.NextDouble() * sumProb;
        double currentSum = 0;
        for (var i = 0; i < candidates.Count; i++)
        {
            currentSum += probabilities[i];
            if (rand <= currentSum) return candidates[i];
        }

        // Fallback in case of floating point rounding errors.
        return candidates.Last();
    }

    private void UpdatePheromones(IEnumerable<(List<string> Path, double Score)> antsResults)
    {
        // 1. Evaporation: Decrease pheromone on all edges to forget bad paths over time.
        foreach (var key in _pheromones.Keys)
        {
            _pheromones[key] *= 1.0 - EvaporationRate;
            // Cleanup trace amounts to save memory.
            if (_pheromones[key] < 0.001) _pheromones.TryRemove(key, out _);
        }

        // 2. Deposit: Strengthen paths taken by ants in this iteration.
        foreach (var (path, deposit) in antsResults)
            for (var i = 0; i < path.Count - 1; i++)
            {
                var u = path[i];
                var v = path[i + 1];
                var key = GetEdgeKey(u, v);

                // Add new pheromone to the existing level.
                _pheromones.AddOrUpdate(key, deposit, (_, oldVal) => oldVal + deposit);
            }
    }

    // Generates a unique key for an undirected edge (A-B is the same as B-A).
    private static string GetEdgeKey(string a, string b)
    {
        return string.CompareOrdinal(a, b) < 0 ? $"{a}-{b}" : $"{b}-{a}";
    }
}