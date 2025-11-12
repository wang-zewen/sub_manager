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

# Function to deploy with Docker Compose
deploy_docker_compose() {
    echo "🐳 Deploying with Docker Compose..."
    echo ""

    if ! command_exists docker-compose && ! docker compose version >/dev/null 2>&1; then
        echo "❌ Docker Compose not found. Please install Docker Compose first."
        echo "Visit: https://docs.docker.com/compose/install/"
        exit 1
    fi

    # Create data directory
    mkdir -p data

    # Build and start
    if docker compose version >/dev/null 2>&1; then
        docker compose up -d --build
    else
        docker-compose up -d --build
    fi

    echo ""
    echo "✅ Deployment successful!"
    echo "🌐 Access the application at: http://localhost:8080"
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
        -p 8080:8080 \
        -v "$(pwd)/data:/app/data" \
        --restart unless-stopped \
        subscription-manager:latest

    echo ""
    echo "✅ Deployment successful!"
    echo "🌐 Access the application at: http://localhost:8080"
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
    java -jar target/subscription-manager-1.0.0.jar

    echo ""
    echo "✅ Application started!"
    echo "🌐 Access at: http://localhost:8080"
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
