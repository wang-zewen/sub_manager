#!/bin/bash

# Subscription Manager - Quick Install Script
# This script downloads and runs the latest release

set -e

REPO="wang-zewen/sub_manager"
APP_NAME="subscription-manager"
DEFAULT_PORT=8080

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
    wget -q -O "$JAR_NAME" "$DOWNLOAD_URL"
elif command -v curl &> /dev/null; then
    curl -s -L -o "$JAR_NAME" "$DOWNLOAD_URL"
else
    echo "ERROR: Neither wget nor curl is available. Please install one of them."
    exit 1
fi

echo "[OK] Download complete: ${JAR_NAME}"
echo ""

# Port configuration
echo "================================================"
echo "  Port Configuration"
echo "================================================"
echo ""
echo "Select port configuration:"
echo "1) Use default port (8080)"
echo "2) Specify custom port"
echo ""
read -p "Enter your choice [1-2] (default: 1): " port_choice

if [ "$port_choice" = "2" ]; then
    read -p "Enter port number (e.g., 9090): " CUSTOM_PORT
    if ! [[ "$CUSTOM_PORT" =~ ^[0-9]+$ ]] || [ "$CUSTOM_PORT" -lt 1024 ] || [ "$CUSTOM_PORT" -gt 65535 ]; then
        echo "ERROR: Invalid port number. Using default port 8080."
        APP_PORT=$DEFAULT_PORT
    else
        APP_PORT=$CUSTOM_PORT
    fi
else
    APP_PORT=$DEFAULT_PORT
fi

echo "[OK] Using port: $APP_PORT"
echo ""

# Check if port is already in use
if command -v lsof &> /dev/null; then
    if lsof -Pi :$APP_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "WARNING: Port $APP_PORT is already in use!"
        read -p "Do you want to specify a different port? [Y/n] " -n 1 -r
        echo ""
        if [[ $REPLY =~ ^[Yy]$ ]] || [[ -z $REPLY ]]; then
            read -p "Enter a different port number: " NEW_PORT
            if [[ "$NEW_PORT" =~ ^[0-9]+$ ]] && [ "$NEW_PORT" -ge 1024 ] && [ "$NEW_PORT" -le 65535 ]; then
                APP_PORT=$NEW_PORT
                echo "[OK] Using port: $APP_PORT"
            else
                echo "ERROR: Invalid port. Exiting."
                exit 1
            fi
        fi
    fi
elif command -v netstat &> /dev/null; then
    if netstat -tuln | grep -q ":$APP_PORT "; then
        echo "WARNING: Port $APP_PORT appears to be in use!"
        echo "You may need to stop the existing service or choose a different port."
    fi
fi

echo ""

# Credentials configuration
echo "================================================"
echo "  Security Configuration"
echo "================================================"
echo ""
echo "Configure login credentials:"
echo "1) Use default credentials (admin/admin123)"
echo "2) Set custom credentials"
echo ""
read -p "Enter your choice [1-2] (default: 1): " auth_choice

if [ "$auth_choice" = "2" ]; then
    read -p "Enter username: " CUSTOM_USERNAME
    read -sp "Enter password: " CUSTOM_PASSWORD
    echo ""
    if [ -z "$CUSTOM_USERNAME" ] || [ -z "$CUSTOM_PASSWORD" ]; then
        echo "ERROR: Username and password cannot be empty. Using defaults."
        APP_USERNAME="admin"
        APP_PASSWORD="admin123"
    else
        APP_USERNAME="$CUSTOM_USERNAME"
        APP_PASSWORD="$CUSTOM_PASSWORD"
    fi
else
    APP_USERNAME="admin"
    APP_PASSWORD="admin123"
fi

echo "[OK] Username: $APP_USERNAME"
echo ""

# Create systemd service file (optional)
echo "================================================"
echo "  Deployment Options"
echo "================================================"
echo ""
echo "How would you like to run the application?"
echo "1) Background process with nohup (simple)"
echo "2) Create systemd service (recommended for servers)"
echo "3) Foreground (manual control)"
echo ""
read -p "Enter your choice [1-3] (default: 1): " deploy_choice

case $deploy_choice in
    2)
        # Systemd service
        if [ "$EUID" -ne 0 ]; then
            echo "ERROR: Creating systemd service requires root privileges."
            echo "Please run with sudo or choose option 1."
            exit 1
        fi

        SERVICE_NAME="subscription-manager"
        SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
        INSTALL_DIR="$(pwd)"
        CURRENT_USER="${SUDO_USER:-$USER}"

        cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=Subscription Manager
After=network.target

[Service]
Type=simple
User=$CURRENT_USER
WorkingDirectory=$INSTALL_DIR
Environment="SERVER_PORT=$APP_PORT"
Environment="APP_SECURITY_USERNAME=$APP_USERNAME"
Environment="APP_SECURITY_PASSWORD=$APP_PASSWORD"
ExecStart=/usr/bin/java -jar $INSTALL_DIR/$JAR_NAME
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

        systemctl daemon-reload
        systemctl enable "$SERVICE_NAME"
        systemctl start "$SERVICE_NAME"

        echo ""
        echo "================================================"
        echo "  Deployment Successful!"
        echo "================================================"
        echo ""
        echo "Service: $SERVICE_NAME"
        echo "Status: systemctl status $SERVICE_NAME"
        echo "Logs: journalctl -u $SERVICE_NAME -f"
        echo "Stop: systemctl stop $SERVICE_NAME"
        echo "Restart: systemctl restart $SERVICE_NAME"
        echo ""
        echo "Access: http://localhost:$APP_PORT"
        echo "Username: $APP_USERNAME"
        echo "Password: $APP_PASSWORD"
        echo ""
        ;;

    3)
        # Foreground
        echo ""
        echo "Starting in foreground mode..."
        echo "Press Ctrl+C to stop"
        echo ""
        echo "Access: http://localhost:$APP_PORT"
        echo "Username: $APP_USERNAME"
        echo "Password: $APP_PASSWORD"
        echo ""

        SERVER_PORT=$APP_PORT \
        APP_SECURITY_USERNAME=$APP_USERNAME \
        APP_SECURITY_PASSWORD=$APP_PASSWORD \
        java -jar "$JAR_NAME"
        ;;

    *)
        # Background with nohup (default)
        echo ""
        echo "Starting application in background..."

        # Create data directory
        mkdir -p data

        # Start in background
        SERVER_PORT=$APP_PORT \
        APP_SECURITY_USERNAME=$APP_USERNAME \
        APP_SECURITY_PASSWORD=$APP_PASSWORD \
        nohup java -jar "$JAR_NAME" > subscription-manager.log 2>&1 &

        PID=$!
        echo $PID > subscription-manager.pid

        # Wait a moment and check if process is running
        sleep 2
        if ps -p $PID > /dev/null; then
            echo ""
            echo "================================================"
            echo "  Deployment Successful!"
            echo "================================================"
            echo ""
            echo "[OK] Application is running in background"
            echo "PID: $PID (saved to subscription-manager.pid)"
            echo "Logs: tail -f subscription-manager.log"
            echo ""
            echo "Access: http://localhost:$APP_PORT"
            echo "Username: $APP_USERNAME"
            echo "Password: $APP_PASSWORD"
            echo ""
            echo "Management commands:"
            echo "  View logs:    tail -f subscription-manager.log"
            echo "  Stop:         kill \$(cat subscription-manager.pid)"
            echo "  Check status: ps -p \$(cat subscription-manager.pid)"
            echo ""
            echo "IMPORTANT: Change the default password in production!"
            echo ""
        else
            echo "ERROR: Application failed to start. Check subscription-manager.log for details."
            cat subscription-manager.log
            exit 1
        fi
        ;;
esac
