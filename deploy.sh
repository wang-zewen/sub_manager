#!/bin/bash

# Subscription Manager - One-Click Deploy Script
# This script provides easy deployment options for the Subscription Manager

set -e

echo "================================================"
echo "   Subscription Manager - Deployment Script    "
echo "================================================"
echo ""

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Configuration
configure_settings() {
    echo "⚙️  Configuration"
    echo ""

    # Port configuration
    echo "Select port configuration:"
    echo "1) Use default port (8080)"
    echo "2) Use custom port"
    read -p "Enter your choice [1-2]: " port_choice

    if [ "$port_choice" = "2" ]; then
        read -p "Enter custom port (e.g., 9090): " CUSTOM_PORT
        export SERVER_PORT=$CUSTOM_PORT
        APP_PORT=$CUSTOM_PORT
    else
        export SERVER_PORT=8080
        APP_PORT=8080
    fi

    echo ""
    echo "🔐 Security Configuration"
    echo ""
    echo "Configure login credentials:"
    echo "1) Use default (username: admin, password: admin123)"
    echo "2) Set custom credentials"
    read -p "Enter your choice [1-2]: " auth_choice

    if [ "$auth_choice" = "2" ]; then
        read -p "Enter username: " CUSTOM_USERNAME
        read -sp "Enter password: " CUSTOM_PASSWORD
        echo ""
        export APP_SECURITY_USERNAME=$CUSTOM_USERNAME
        export APP_SECURITY_PASSWORD=$CUSTOM_PASSWORD
    else
        export APP_SECURITY_USERNAME=admin
        export APP_SECURITY_PASSWORD=admin123
    fi

    echo ""
}

# Function to deploy with Docker Compose
deploy_docker_compose() {
    echo "🐳 Deploying with Docker Compose..."
    echo ""

    if ! command_exists docker-compose && ! docker compose version >/dev/null 2>&1; then
        echo "❌ Docker Compose not found. Please install Docker Compose first."
        echo "Visit: https://docs.docker.com/compose/install/"
        exit 1
    fi

    # Configure settings
    configure_settings

    # Create data directory
    mkdir -p data

    # Create or update docker-compose.override.yml with custom settings
    cat > docker-compose.override.yml <<EOF
version: '3.8'
services:
  subscription-manager:
    ports:
      - "${APP_PORT}:8080"
    environment:
      - SERVER_PORT=8080
      - APP_SECURITY_USERNAME=${APP_SECURITY_USERNAME}
      - APP_SECURITY_PASSWORD=${APP_SECURITY_PASSWORD}
EOF

    # Build and start
    if docker compose version >/dev/null 2>&1; then
        docker compose up -d --build
    else
        docker-compose up -d --build
    fi

    echo ""
    echo "✅ Deployment successful!"
    echo "🌐 Access the application at: http://localhost:${APP_PORT}"
    echo "👤 Username: ${APP_SECURITY_USERNAME}"
    echo "🔑 Password: ${APP_SECURITY_PASSWORD}"
    echo ""
    echo "Useful commands:"
    echo "  - View logs: docker compose logs -f"
    echo "  - Stop: docker compose down"
    echo "  - Restart: docker compose restart"
}

# Function to deploy with Docker
deploy_docker() {
    echo "🐳 Deploying with Docker..."
    echo ""

    if ! command_exists docker; then
        echo "❌ Docker not found. Please install Docker first."
        echo "Visit: https://docs.docker.com/get-docker/"
        exit 1
    fi

    # Configure settings
    configure_settings

    # Build image
    docker build -t subscription-manager:latest .

    # Stop and remove existing container
    docker stop subscription-manager 2>/dev/null || true
    docker rm subscription-manager 2>/dev/null || true

    # Create data directory
    mkdir -p data

    # Run container
    docker run -d \
        --name subscription-manager \
        -p "${APP_PORT}:8080" \
        -e SERVER_PORT=8080 \
        -e APP_SECURITY_USERNAME="${APP_SECURITY_USERNAME}" \
        -e APP_SECURITY_PASSWORD="${APP_SECURITY_PASSWORD}" \
        -v "$(pwd)/data:/app/data" \
        --restart unless-stopped \
        subscription-manager:latest

    echo ""
    echo "✅ Deployment successful!"
    echo "🌐 Access the application at: http://localhost:${APP_PORT}"
    echo "👤 Username: ${APP_SECURITY_USERNAME}"
    echo "🔑 Password: ${APP_SECURITY_PASSWORD}"
    echo ""
    echo "Useful commands:"
    echo "  - View logs: docker logs -f subscription-manager"
    echo "  - Stop: docker stop subscription-manager"
    echo "  - Start: docker start subscription-manager"
    echo "  - Restart: docker restart subscription-manager"
}

# Function to deploy with JAR
deploy_jar() {
    echo "☕ Building and running JAR..."
    echo ""

    if ! command_exists mvn && ! command_exists java; then
        echo "❌ Java/Maven not found. Please install Java 17+ and Maven."
        exit 1
    fi

    # Configure settings
    configure_settings

    # Build JAR
    if command_exists mvn; then
        echo "📦 Building with Maven..."
        mvn clean package -DskipTests
    else
        echo "❌ Maven not found. Please build the JAR manually or use Docker."
        exit 1
    fi

    # Create data directory
    mkdir -p data

    # Run JAR
    echo "🚀 Starting application..."
    echo ""
    echo "✅ Configuration:"
    echo "🌐 Port: ${APP_PORT}"
    echo "👤 Username: ${APP_SECURITY_USERNAME}"
    echo "🔑 Password: ${APP_SECURITY_PASSWORD}"
    echo ""
    echo "Press Ctrl+C to stop"
    echo ""

    SERVER_PORT=$APP_PORT \
    APP_SECURITY_USERNAME=$APP_SECURITY_USERNAME \
    APP_SECURITY_PASSWORD=$APP_SECURITY_PASSWORD \
    java -jar target/subscription-manager-1.0.0.jar
}

# Main menu
echo "Select deployment method:"
echo "1) Docker Compose (Recommended)"
echo "2) Docker"
echo "3) JAR (Java required)"
echo "4) Exit"
echo ""
read -p "Enter your choice [1-4]: " choice

case $choice in
    1)
        deploy_docker_compose
        ;;
    2)
        deploy_docker
        ;;
    3)
        deploy_jar
        ;;
    4)
        echo "Exiting..."
        exit 0
        ;;
    *)
        echo "❌ Invalid choice. Exiting..."
        exit 1
        ;;
esac
