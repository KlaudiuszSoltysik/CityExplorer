# 🏙️ City Explorer

![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)
![Tech Stack](https://img.shields.io/badge/Stack-Kotlin%20|%20.NET%20|%20Python-blue)

> **Goal:** Gamify urban exploration by turning the real world into a hexagonal strategy board using GPS location.

## 📖 About

City Explorer is a mobile platform designed to encourage physical activity through location-based competition. The world is divided into a hexagonal grid, where users compete to "conquer" territories by physically spending time within them. The system dynamically weights areas based on their real-world popularity (POIs).

## 📸 Screenshots

_(Screenshots will be added soon)_

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
- **Docker:** Separate containers for Development and Production environments with auto-deploy on changes.

## 🚧 Roadmap (Upcoming Features)

- **Monetization:** Implementation of Consumable In-App Purchases (Google Play Billing Library).
- **Automated Testing:** Expanding CI pipeline with Unit & Integration tests for Backend and Mobile.
- **Social Features:** Guilds and team-based competitions.
- **Swagger/OpenAPI:** Auto-generated API documentation for easy client integration.

## 💻 Tech Stack

| Domain       | Technology                                                    |
| :----------- | :------------------------------------------------------------ |
| **Mobile**   | Kotlin, Jetpack Compose, Google Maps SDK, Google OAuth        |
| **Backend**  | C# .NET 9.0, Entity Framework Core                            |
| **Data**     | Python, Uber H3, Overpass API                                 |
| **Database** | PostgreSQL                                                    |
| **DevOps**   | Docker, Docker Compose, Terraform, Cloudflare, GitHub Actions |
