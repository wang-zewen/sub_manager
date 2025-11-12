#!/bin/bash

# Subscription Manager - Quick Install Script
# This script downloads and runs the latest release

set -e

REPO="wang-zewen/sub_manager"
APP_NAME="subscription-manager"

echo "================================================"
echo "   Subscription Manager - Quick Install        "
echo "================================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed!"
    echo "Please install Java 17 or higher:"
    echo "  - Oracle JDK: https://www.oracle.com/java/technologies/downloads/"
    echo "  - OpenJDK: https://adoptium.net/"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java version is too old (found: $JAVA_VERSION, required: 17+)"
    echo "Please upgrade Java to version 17 or higher"
    exit 1
fi

echo "[OK] Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Get latest release URL
echo "Fetching latest release..."
LATEST_RELEASE=$(curl -s "https://api.github.com/repos/${REPO}/releases/latest")

if [ -z "$LATEST_RELEASE" ]; then
    echo "ERROR: Failed to fetch latest release information"
    exit 1
fi

# Extract download URL (exclude .sha256 files)
DOWNLOAD_URL=$(echo "$LATEST_RELEASE" | grep "browser_download_url.*\.jar\"" | grep -v "\.sha256" | head -n 1 | cut -d '"' -f 4)
VERSION=$(echo "$LATEST_RELEASE" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

if [ -z "$DOWNLOAD_URL" ]; then
    echo "ERROR: Could not find JAR file in latest release"
    echo "Please download manually from: https://github.com/${REPO}/releases"
    exit 1
fi

echo "Latest version: $VERSION"
echo "Download URL: $DOWNLOAD_URL"
echo ""

# Download JAR
JAR_NAME="${APP_NAME}-${VERSION#v}.jar"
echo "Downloading ${JAR_NAME}..."

if command -v wget &> /dev/null; then
    wget -O "$JAR_NAME" "$DOWNLOAD_URL"
elif command -v curl &> /dev/null; then
    curl -L -o "$JAR_NAME" "$DOWNLOAD_URL"
else
    echo "ERROR: Neither wget nor curl is available. Please install one of them."
    exit 1
fi

echo "[OK] Download complete: ${JAR_NAME}"
echo ""

# Display login information
echo "================================================"
echo "  Installation Complete!"
echo "================================================"
echo ""
echo "Default Login Credentials:"
echo "  Username: admin"
echo "  Password: admin123"
echo ""
echo "IMPORTANT: Change the default password in production!"
echo ""
echo "To start the application, run:"
echo "  java -jar ${JAR_NAME}"
echo ""
echo "Then access at: http://localhost:8080"
echo ""
echo "For custom configuration, use environment variables:"
echo "  SERVER_PORT=9090 \\"
echo "  APP_SECURITY_USERNAME=myuser \\"
echo "  APP_SECURITY_PASSWORD=mypassword \\"
echo "  java -jar ${JAR_NAME}"
echo ""

# Ask user if they want to run now (only if not running as root)
if [ "$EUID" -eq 0 ]; then
    echo "NOTE: Running as root. Please run the application as a regular user."
    exit 0
fi

read -p "Do you want to start the application now? [Y/n] " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]] || [[ -z $REPLY ]]; then
    echo "Starting Subscription Manager..."
    echo "Access at: http://localhost:8080"
    echo "Default Login: admin / admin123"
    echo "Press Ctrl+C to stop"
    echo ""
    java -jar "$JAR_NAME"
fi
