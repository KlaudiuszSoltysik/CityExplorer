# City Explorer - university project

City Explorer is a mobile application designed to gamify urban exploration through location-based competition.

## Core Functionality:

- Location-Based Discovery: The primary mechanic involves users discovering geographical areas based on their physical presence.
- Geographical Model: Each supported city is overlaid with a tessellation of hexagons.
- Hexagon Weighting: Each hexagon is assigned a unique weight.
- Weight Calculation: weight is dynamically calculated based on the count and predetermined importance scoring of Points of Interest (POIs) contained within the hexagon's boundaries.
- Discovery Mechanism: A user "discovers" a hexagon by maintaining GPS presence within its boundaries for a variable duration.
- Duration Dependency: The required discovery time is a function of the hexagon's weight,
- Competition Model: Users accumulate points based on discovered hexagons, competing across two distinct leaderboards:
  - Tourist Category: Prioritizes hexagons covering high-importance POIs (major attractions).
  - Local Category: Prioritizes hexagons covering low-to-medium importance POIs (local spots, less-known areas).

## During development
