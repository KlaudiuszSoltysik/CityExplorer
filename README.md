# City Explorer (during development)

![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)
![Tech Stack](https://img.shields.io/badge/Stack-Kotlin%20|%20.NET%20|%20Python-blue)

Gamify urban exploration by turning the real world into a hexagonal strategy board using GPS location.

## 📲 Download application

<a href="https://github.com/KlaudiuszSoltysik/CityExplorer/releases/download/beta/app-release.apk"><img src="https://img.shields.io/badge/Download%20.APK-3DDC84?style=for-the-badge&logo=android&logoColor=white" height="40"></a><br><br>

<img src="https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=https://github.com/KlaudiuszSoltysik/CityExplorer/releases/download/beta/app-release.apk" width="150" alt="QR Code" />

> _You have to allow instalation from unknown sources._

## 📖 About

City Explorer is a mobile platform designed to encourage physical activity through location-based competition. The world is divided into a hexagonal grid, where users compete to "conquer" territories by physically spending time within them. The system dynamically weights areas based on their real-world popularity (POIs).

## 📸 Screenshots

_screenshots_

## ⚙️ Core Mechanics

The system relies on a complex geospatial model rather than simple coordinates:

- **⬡ Hexagonal World Model:** Cities are tessellated into hexagons, creating a discrete grid for gameplay.
- **⚖️ Dynamic Weighting Algorithm:** Each hexagon has a unique value calculated via Python scripts, analyzing the density of Points of Interest (POIs) extracted from OpenStreetMap.
- **⏱️ Time-Based Discovery:** To "claim" a hexagon, a user must maintain GPS presence within its boundaries. The required duration scales dynamically with the hexagon's importance (weight).
- **🏆 Competitive Leaderboards:** Real-time scoring system based on the quality and quantity of discovered territories.

## 🚀 Technical Highlights

### 📱 Mobile App (Kotlin & Jetpack Compose)

Built with a focus on performance and battery efficiency during background tracking.

- **Background Location Services:** Robust state management for tracking user location even when the app is minimized (Foreground Service).
- **Smart Caching:** Implemented local caching strategy to minimize API calls and data usage.
- **Google Maps SDK:** Custom styling and overlay management for rendering hexagonal grids.
- **Authentication:** Secure sign-in flow implemented via **Google OAuth**.
- **Architecture:** Uses modern Jetpack Compose for UI and Coroutines for asynchronous tasks.
- **Deployment:** Downloadable release version of application.

### 🔙 Backend (.NET 9.0 & PostgreSQL)

High-performance REST API designed for throughput and scalability.

- **Uber H3 Integration:** Server-side spatial indexing for fast geospatial queries and validation.
- **Background Workers:** Dedicated services for managing session states and cleaning up stale data.
- **Security:** JWT Token Authentication and secure session management.

### 🐍 Data Engineering (Python)

Scripts responsible for world generation and data analysis.

- **Data Pipeline:** Fetches data from **Overpass API (OSM)** to identify POIs.
- **Spatial Analysis:** Calculates hexagon weights using Uber H3 library based on POI density.

### 🛠️ DevOps & Infrastructure

Fully dockerized environment with automated pipelines.

- **Infrastructure as Code:** **Terraform** used to configure Cloudflare Tunnel for secure exposure of local services.
- **CI/CD:** GitHub Actions configured for automated building.
- **Docker:** Separate containers for independent Development and Production environments with auto-deploy on changes.
- **Server:** Backend and database are hosted on-premise.

## 💻 Tech Stack

| Domain       | Technology                                                    |
| :----------- | :------------------------------------------------------------ |
| **Mobile**   | Kotlin, Jetpack Compose, Google Maps SDK, Google OAuth        |
| **Backend**  | C# .NET 9.0, Entity Framework Core, Uber H3                   |
| **Data**     | Python, Uber H3, Overpass API                                 |
| **Database** | PostgreSQL                                                    |
| **DevOps**   | Docker, Docker Compose, Terraform, Cloudflare, GitHub Actions |

## 🚀 Project Roadmap (Portfolio Strategy)

### 🔴 Mandatory (MVP Core)

- [ ] **Core Logic Stability**
    - [ ] **Prevent User Token Expiration:** Ensure the session remains valid during active gameplay/exploration to prevent abrupt logouts.
    - [ ] **Implement "3-Strike" Rule:** Circuit breaker pattern. If 3 consecutive batch uploads fail, transition Service to `SUSPENDED` to stop API spam.
    - [ ] **Battery Optimization (GPS Suspend):** When `SUSPENDED`, physically stop `client.removeLocationUpdates()` to verify background service efficiency.
    - [ ] **Auto-Resume Logic:** Reset error counters and re-enable GPS upon the first successful network "heartbeat" (200 OK).
    - [ ] **Provider State Monitoring:** Handle system-wide GPS toggle off. Pause game gracefully instead of crashing or recording invalid data.
- [ ] **User Screen & Compliance**
    - [ ] **User Stats Dashboard:** Display basic stats (hexes claimed, distance walked) to close the gameplay loop.
    - [ ] **Delete Account Option:** **CRITICAL.** Required by Google Play policy. Must function completely (API call to scrub data).

### 🟡 High Impact / Engineering Flex

- [ ] **Architecture & Refactoring**
    - [ ] **Hilt Migration (DI):** Refactor manual dependency injection to Hilt. Shows mastery of modern Android standards and cleaner architecture.
- [ ] **Quality Assurance (Testing)**
    - [ ] **Unit Tests:** Add JUnit tests for core logic (e.g., Hexagon weight calculation, Service state transitions).
    - [ ] **Integration Tests:** Basic tests for Backend API endpoints to ensure non-breaking changes.
- [ ] **DevOps & CI**
    - [ ] **GitHub Actions (CI):** Automate application release version.
- [ ] **Observability**
    - [ ] **Error Tracking:** Integrate **Sentry** or **Firebase Crashlytics**. Demonstrates proactivity in monitoring app health in production.
- [ ] **Documentation**
    - [ ] **Scalar/Swagger:** expose auto-generated API documentation. Shows respect for Developer Experience (DX).

### 🟢 Nice to Have / Over-engineering

- [ ] **Features**
    - [ ] **Social Features:** Leaderboards, friends, chat (High effort, low impact if user base is small).
    - [ ] **Monetization:** Google Play Billing implementation (Complex boilerplate, relevant mainly for Fintech/E-commerce roles).
- [ ] **Advanced DevOps**
    - [ ] **Off-site Backups:** S3/GCS backups for the DB (Critical for production business, optional for portfolio).
    - [ ] **Host Monitoring:** Prometheus/Grafana setup (Visual eye-candy, but overkill for a single server).
    - [ ] **DataOps Pipeline:** Automating Python scripts via Cron/Airflow (Manual execution is acceptable for static world generation).
