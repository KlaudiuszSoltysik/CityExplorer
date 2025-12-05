# 🚀 Project Roadmap

## 🔴 Mandatory (MVP Core)

- **Core Logic** – Implementation of primary application features.
  - **Handle API Response:** Implement logic to parse the batch upload response (which contains updated hexagon data) and update cache.
  - **Failover for `saveAll`:** Modify the `onDestroy/saveAll` logic. If the final network request fails, immediately serialize the buffer to cache.
  - **Startup Recovery:** When the Service starts, check for existing data from previous sessions and inject them at the front of the transmission queue.
  - **Implement "3-Strike" Rule:** Introduce a failure counter in the Service. If 3 consecutive batch uploads fail, transition the Service state to `SUSPENDED`.
  - **Battery Optimization:** When in `SUSPENDED` state, physically stop GPS updates (`client.removeLocationUpdates()`) to save battery, but keep the network retry loop active.
  - **Auto-Resume:** Upon the first successful API response (`200 OK`), reset the error counter to 0 and re-enable GPS updates (`client.requestLocationUpdates()`).
  - **Reactive UI Updates:** Ensure the `ViewModel` observes the Repository (via `Flow`). The UI should automatically reflect changes (e.g., hexagon color change) when the Repository is updated by the Service, without manual triggers.
  - **Provider State Monitoring:** Register a `BroadcastReceiver` or callback to detect if the user disables GPS system-wide during gameplay. Pause the game and notify the user if this happens.

## 🟡 Nice to Have (Enhancements)

- **Monetization** – "Buy Me a Coffee" button integration.
- **Token Management** – Handling token expiration during active exploration.
- **Social Features** – Community and interaction aspects.
- **Refactoring** – Dependency Injection implementation (Hilt migration).

## 🔵 Deployment & DevOps

- **Git Workflow** – Establish `dev` branch strategy.
- **Dockerization** – Containerize the application.
- **CI/CD Pipeline** – Setup GitHub Actions.
- **Infrastructure** – Terraform implementation.
- **Observability** – Monitoring and logging setup.
