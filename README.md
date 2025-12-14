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

## 🚀 Project Roadmap

### 🔴 Mandatory (MVP Core)

- **Prevent user token from expiration during exploration**
- **Implement "3-Strike" Rule:** Introduce a failure counter in the Service. If 3 consecutive batch uploads fail, transition the Service state to `SUSPENDED`.
- **Battery Optimization:** When in `SUSPENDED` state, physically stop GPS updates (`client.removeLocationUpdates()`) to save battery, but keep the network retry loop active.
- **Auto-Resume:** Upon the first successful API response (`200 OK`), reset the error counter to 0 and re-enable GPS updates (`client.requestLocationUpdates()`).
- **Provider State Monitoring:** Register a `BroadcastReceiver` or callback to detect if the user disables GPS system-wide during gameplay. Pause the game and notify the user if this happens.
- **User Screen:** Screen with user stats, badges, ranking, logout, delete account option etc.

### 🟡 Nice to Have (Enhancements)

- **Token Management** – Handling token expiration during active exploration.
- **Social Features** – Community and interaction aspects.
- **Refactoring** – Dependency Injection implementation (Hilt migration).
- **Monetization:** Implementation of Consumable In-App Purchases (Google Play Billing Library).
- **Automated Testing:** Expanding CI pipeline with Unit & Integration tests for Backend and Mobile.
- **Scalar:** Auto-generated API documentation for easy client integration.
- **GitHub Action:** Action to automatically create release
- **Observability** – Monitoring and logging setup.
- **Backup Strategy:** Automated daily **PostgreSQL backups** pushed to external storage (S3/GCS) to mitigate on-premise hardware failure risks.
- **Error Tracking:** Setup **Sentry** or **Firebase Crashlytics** specifically for capturing unhandled exceptions in the mobile client and backend.
- **DataOps Pipeline:** Automate the Python execution flow to periodically refresh OSM data and re-calculate Hexagon weights without manual intervention.
- **Host Monitoring:** Setup **Prometheus/Node Exporter** to monitor on-premise server resources (Disk usage, RAM, CPU) to prevent outages due to resource exhaustion.
