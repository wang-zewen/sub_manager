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
    echo "❌ Java is not installed!"
    echo "Please install Java 17 or higher:"
    echo "  - Oracle JDK: https://www.oracle.com/java/technologies/downloads/"
    echo "  - OpenJDK: https://adoptium.net/"
    exit 1
fi

# Check Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java version is too old (found: $JAVA_VERSION, required: 17+)"
    echo "Please upgrade Java to version 17 or higher"
    exit 1
fi

echo "✅ Java version: $(java -version 2>&1 | head -n 1)"
echo ""

# Get latest release URL
echo "🔍 Fetching latest release..."
LATEST_RELEASE=$(curl -s "https://api.github.com/repos/${REPO}/releases/latest")

if [ -z "$LATEST_RELEASE" ]; then
    echo "❌ Failed to fetch latest release information"
    exit 1
fi

# Extract download URL (exclude .sha256 files)
DOWNLOAD_URL=$(echo "$LATEST_RELEASE" | grep "browser_download_url.*\.jar\"" | grep -v "\.sha256" | head -n 1 | cut -d '"' -f 4)
VERSION=$(echo "$LATEST_RELEASE" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

if [ -z "$DOWNLOAD_URL" ]; then
    echo "❌ Could not find JAR file in latest release"
    echo "Please download manually from: https://github.com/${REPO}/releases"
    exit 1
fi

echo "📦 Latest version: $VERSION"
echo "📥 Download URL: $DOWNLOAD_URL"
echo ""

# Download JAR
JAR_NAME="${APP_NAME}-${VERSION#v}.jar"
echo "⬇️  Downloading ${JAR_NAME}..."

if command -v wget &> /dev/null; then
    wget -O "$JAR_NAME" "$DOWNLOAD_URL"
elif command -v curl &> /dev/null; then
    curl -L -o "$JAR_NAME" "$DOWNLOAD_URL"
else
    echo "❌ Neither wget nor curl is available. Please install one of them."
    exit 1
fi

echo "✅ Download complete: ${JAR_NAME}"
echo ""

# Ask user if they want to run now
read -p "Do you want to start the application now? [Y/n] " -n 1 -r
echo ""

if [[ $REPLY =~ ^[Yy]$ ]] || [[ -z $REPLY ]]; then
    echo "🚀 Starting Subscription Manager..."
    echo "   Access at: http://localhost:8080"
    echo "   Press Ctrl+C to stop"
    echo ""
    java -jar "$JAR_NAME"
else
    echo ""
    echo "✅ Installation complete!"
    echo ""
    echo "To start the application, run:"
    echo "   java -jar ${JAR_NAME}"
    echo ""
    echo "Then access at: http://localhost:8080"
fi
