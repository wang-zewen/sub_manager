# Subscription Manager

[![Build](https://github.com/wang-zewen/sub_manager/actions/workflows/build.yml/badge.svg)](https://github.com/wang-zewen/sub_manager/actions/workflows/build.yml)
[![Release](https://github.com/wang-zewen/sub_manager/actions/workflows/release.yml/badge.svg)](https://github.com/wang-zewen/sub_manager/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

A web-based subscription link management tool built with Spring Boot. Easily manage, organize, and access your subscription links through a clean and intuitive web interface.

## Features

- **Web UI Management**: Clean and responsive interface for managing subscriptions
- **CRUD Operations**: Create, Read, Update, and Delete subscription links
- **Status Management**: Toggle active/inactive status for subscriptions
- **REST API**: Full RESTful API for programmatic access
- **One-Click Copy**: Quick copy-to-clipboard functionality for URLs
- **Persistent Storage**: H2 database for reliable data storage
- **Multiple Deployment Options**: Docker, Docker Compose, or standalone JAR

## Download

### Quick Install Script (Easiest)

One-command install and run:

```bash
curl -fsSL https://raw.githubusercontent.com/wang-zewen/sub_manager/main/install.sh | bash
```

This script will:
- Check Java installation (Java 17+ required)
- Download the latest release automatically
- Optionally start the application

### Pre-built JAR (Manual Download)

Download the latest pre-built JAR from the [Releases](https://github.com/wang-zewen/sub_manager/releases) page:

1. Go to [Releases](https://github.com/wang-zewen/sub_manager/releases)
2. Download `subscription-manager-x.x.x.jar` from the latest release
3. Run directly: `java -jar subscription-manager-x.x.x.jar`
4. Access at: http://localhost:8080

**Requirements**: Java 17 or higher

### Build from Source

Clone and build the project yourself:

```bash
git clone https://github.com/wang-zewen/sub_manager.git
cd sub_manager
mvn clean package
java -jar target/subscription-manager-1.0.0.jar
```

## Quick Start

### Option 1: One-Click Deploy (Recommended)

```bash
# Clone the repository
git clone <your-repo-url>
cd subscription-manager

# Run the deployment script
./deploy.sh
```

The script will guide you through the deployment process with options for:
- Docker Compose (recommended)
- Docker
- JAR (requires Java 17+)

### Option 2: Docker Compose

```bash
# Start the application
docker compose up -d

# View logs
docker compose logs -f

# Stop the application
docker compose down
```

Access the application at: http://localhost:8080

### Option 3: Docker

```bash
# Build the image
docker build -t subscription-manager .

# Run the container
docker run -d \
  --name subscription-manager \
  -p 8080:8080 \
  -v $(pwd)/data:/app/data \
  subscription-manager
```

### Option 4: Build and Run JAR

```bash
# Build with Maven
mvn clean package

# Run the JAR
java -jar target/subscription-manager-1.0.0.jar
```

## Usage

### Web Interface

1. Open your browser and navigate to `http://localhost:8080`
2. Add a new subscription by filling in the form:
   - **Name**: A friendly name for your subscription
   - **URL**: The subscription link
   - **Description**: (Optional) Additional notes
   - **Active**: Toggle to enable/disable
3. Manage existing subscriptions:
   - **Edit**: Modify subscription details
   - **Toggle**: Activate/deactivate subscriptions
   - **Delete**: Remove subscriptions
   - **Copy**: Quick copy URL to clipboard

### REST API

Base URL: `http://localhost:8080/api/subscriptions`

#### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/subscriptions` | Get all subscriptions |
| GET | `/api/subscriptions/active` | Get active subscriptions only |
| GET | `/api/subscriptions/{id}` | Get subscription by ID |
| POST | `/api/subscriptions` | Create new subscription |
| PUT | `/api/subscriptions/{id}` | Update subscription |
| DELETE | `/api/subscriptions/{id}` | Delete subscription |
| PATCH | `/api/subscriptions/{id}/toggle` | Toggle active status |

#### Example: Create Subscription

```bash
curl -X POST http://localhost:8080/api/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My VPN",
    "url": "https://example.com/subscription",
    "description": "Primary VPN subscription",
    "isActive": true
  }'
```

#### Example: Get All Active Subscriptions

```bash
curl http://localhost:8080/api/subscriptions/active
```

## Configuration

Edit `src/main/resources/application.properties` to customize:

```properties
# Server port
server.port=8080

# Database location
spring.datasource.url=jdbc:h2:file:./data/subscriptions

# Enable/disable H2 console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

## Project Structure

```
subscription-manager/
├── src/
│   ├── main/
│   │   ├── java/com/submanager/subscriptionmanager/
│   │   │   ├── controller/         # Web and API controllers
│   │   │   ├── model/              # Entity classes
│   │   │   ├── repository/         # Data repositories
│   │   │   ├── service/            # Business logic
│   │   │   └── SubscriptionManagerApplication.java
│   │   └── resources/
│   │       ├── static/             # CSS, JS files
│   │       ├── templates/          # HTML templates
│   │       └── application.properties
│   └── test/
├── Dockerfile
├── docker-compose.yml
├── deploy.sh
└── pom.xml
```

## Technology Stack

- **Backend**: Spring Boot 3.2.0
- **Frontend**: Thymeleaf, Bootstrap 5, Font Awesome
- **Database**: H2 (embedded)
- **Build Tool**: Maven
- **Java Version**: 17+

## Development

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker (optional, for containerized deployment)

### Run in Development Mode

```bash
mvn spring-boot:run
```

The application will start with hot-reload enabled on port 8080.

### Build

```bash
mvn clean package
```

The JAR file will be created in `target/subscription-manager-1.0.0.jar`

## Creating a Release

To create a new release with auto-generated JAR:

### Method 1: Create Git Tag (Automated)

```bash
# Create and push a version tag
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions will automatically:
- Build the project
- Run tests
- Generate JAR file
- Create a GitHub Release
- Upload the JAR as a release asset

### Method 2: Manual Release (GitHub UI)

1. Go to your repository on GitHub
2. Click "Actions" > "Release" workflow
3. Click "Run workflow"
4. Enter the version (e.g., v1.0.0)
5. Click "Run workflow"

The JAR will be automatically built and attached to the release.

## Data Persistence

Subscription data is stored in an H2 database located at `./data/subscriptions.mv.db`.

**Backup your data**: Simply copy the `data/` directory to preserve your subscriptions.

## H2 Console (Optional)

For database debugging, access the H2 console at: http://localhost:8080/h2-console

- **JDBC URL**: `jdbc:h2:file:./data/subscriptions`
- **Username**: `sa`
- **Password**: (leave empty)

## Troubleshooting

### Port 8080 already in use

Change the port in `application.properties`:
```properties
server.port=9090
```

Or set via environment variable:
```bash
SERVER_PORT=9090 java -jar target/subscription-manager-1.0.0.jar
```

### Permission denied on deploy.sh

```bash
chmod +x deploy.sh
```

## License

MIT License - feel free to use this project for any purpose.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

If you encounter any issues or have questions, please open an issue on GitHub.

---

**Made with ❤️ using Spring Boot**
