#!/bin/bash

# Script to fix placeholder strings in cordova-plugin-shared files copied by Capacitor
# This script should be run after 'npx cap sync' to replace __BUNDLE_IDENTIFIER__ and __URL_SCHEME__
# It also handles initial setup by copying Share Extension files if they don't exist

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if cordova-plugin-shared is installed
PLUGIN_DIR="node_modules/cordova-plugin-shared"
if [ ! -d "$PLUGIN_DIR" ]; then
    echo -e "${RED}ERROR:${NC} cordova-plugin-shared plugin is not installed."
    echo ""
    echo "This script is configured to run automatically after 'npx cap sync' via the"
    echo "'capacitor:sync:after' hook in package.json."
    echo ""
    echo "You have two options:"
    echo ""
    echo "1. ${GREEN}Install the plugin:${NC}"
    echo "   npm install cordova-plugin-shared"
    echo "   # or"
    echo "   cordova plugin add cordova-plugin-shared"
    echo ""
    echo "2. ${YELLOW}Remove this script from package.json${NC} (if you don't need the plugin):"
    echo "   Remove the following line from package.json:"
    echo "   \"capacitor:sync:after\": \"./node_modules/cordova-plugin-shared/scripts/fix-cordova-plugin-shared.sh\""
    echo ""
    exit 1
fi

# Plugin source directory
PLUGIN_SOURCE_DIR="$PLUGIN_DIR/src/ios/ShareExtension"
SHAREEXT_DIR="ios/App/ShareExt"

# Function to replace placeholders in a file
replace_placeholders() {
    local file="$1"
    local bundle_id="$2"
    local url_scheme="$3"
    local display_name="${4:-Share}"
    local version="${5:-1.0.0}"
    local build_number="${6:-1}"
    
    if [ ! -f "$file" ]; then
        return 1
    fi
    
    # Check if file contains placeholders
    if grep -q "__BUNDLE_IDENTIFIER__\|__URL_SCHEME__\|__DISPLAY_NAME__\|__BUNDLE_SHORT_VERSION_STRING__\|__BUNDLE_VERSION__" "$file"; then
        # Create backup
        cp "$file" "$file.bak"
        
        # Determine sed command based on OS
        if [[ "$OSTYPE" == "darwin"* ]]; then
            SED_CMD="sed -i ''"
        else
            SED_CMD="sed -i"
        fi
        
        # Replace placeholders
        $SED_CMD "s/group\.__BUNDLE_IDENTIFIER__/group.$bundle_id/g" "$file"
        $SED_CMD "s/__BUNDLE_IDENTIFIER__/$bundle_id/g" "$file"
        $SED_CMD "s/__URL_SCHEME__/$url_scheme/g" "$file"
        $SED_CMD "s/__DISPLAY_NAME__/$display_name/g" "$file"
        $SED_CMD "s/__BUNDLE_SHORT_VERSION_STRING__/$version/g" "$file"
        $SED_CMD "s/__BUNDLE_VERSION__/$build_number/g" "$file"
        
        # Verify replacement
        if grep -q "__BUNDLE_IDENTIFIER__\|__URL_SCHEME__\|__DISPLAY_NAME__\|__BUNDLE_SHORT_VERSION_STRING__\|__BUNDLE_VERSION__" "$file"; then
            echo -e "${RED}Error:${NC} Some placeholders were not replaced in $file. Restoring backup."
            mv "$file.bak" "$file"
            return 1
        fi
        
        # Remove backup on success
        rm "$file.bak"
        return 0
    fi
    
    return 0
}

# Try to extract bundle ID from Xcode project
# Look for ShareExt target's PRODUCT_BUNDLE_IDENTIFIER
BUNDLE_ID=$(grep -A 5 "ShareExt" ios/App/App.xcodeproj/project.pbxproj 2>/dev/null | grep "PRODUCT_BUNDLE_IDENTIFIER" | head -1 | sed 's/.*PRODUCT_BUNDLE_IDENTIFIER = \([^;]*\);.*/\1/' | xargs)

# If not found, try to get it from the ShareExt Info.plist or from the manually configured file
if [ -z "$BUNDLE_ID" ] || [ "$BUNDLE_ID" = "" ]; then
    # Try reading from the manually configured ShareViewController.h in App/ShareExt
    if [ -f "$SHAREEXT_DIR/ShareViewController.h" ]; then
        BUNDLE_ID=$(grep "SHAREEXT_GROUP_IDENTIFIER" "$SHAREEXT_DIR/ShareViewController.h" | sed 's/.*@"group\.\([^"]*\)".*/\1/')
    fi
fi

# Extract URL scheme from the manually configured file or try to get from Capacitor config
URL_SCHEME=""
if [ -f "$SHAREEXT_DIR/ShareViewController.h" ]; then
    URL_SCHEME=$(grep "SHAREEXT_URL_SCHEME" "$SHAREEXT_DIR/ShareViewController.h" | sed 's/.*@"\([^"]*\)".*/\1/')
fi

# If still not found, try to get from Capacitor config
if [ -z "$URL_SCHEME" ] || [ "$URL_SCHEME" = "" ]; then
    if [ -f "capacitor.config.ts" ]; then
        URL_SCHEME=$(grep -i "scheme" capacitor.config.ts | head -1 | sed "s/.*['\"]\([^'\"]*\)['\"].*/\1/" | xargs)
    fi
fi

# Get app name for display name (optional)
DISPLAY_NAME="Share"
if [ -f "capacitor.config.ts" ]; then
    DISPLAY_NAME=$(grep -i "appName" capacitor.config.ts | head -1 | sed "s/.*['\"]\([^'\"]*\)['\"].*/\1/" | xargs)
    if [ -z "$DISPLAY_NAME" ] || [ "$DISPLAY_NAME" = "" ]; then
        DISPLAY_NAME="Share"
    fi
fi

# Get version info (optional, defaults provided)
VERSION="1.0.0"
BUILD_NUMBER="1"

# Check if we have the required values
if [ -z "$BUNDLE_ID" ] || [ "$BUNDLE_ID" = "" ]; then
    echo -e "${RED}Error:${NC} Could not determine bundle identifier."
    echo "Please ensure ShareExt target is configured in Xcode with PRODUCT_BUNDLE_IDENTIFIER."
    exit 1
fi

if [ -z "$URL_SCHEME" ] || [ "$URL_SCHEME" = "" ]; then
    echo -e "${RED}Error:${NC} Could not determine URL scheme."
    echo "Please ensure capacitor.config.ts contains 'ios.scheme' or ShareViewController.h contains SHAREEXT_URL_SCHEME."
    exit 1
fi

echo -e "${GREEN}Using configuration:${NC}"
echo "  Bundle ID: $BUNDLE_ID"
echo "  URL Scheme: $URL_SCHEME"
echo "  Display Name: $DISPLAY_NAME"

# Check if ShareExt directory exists and has files
SHAREEXT_EXISTS=false
if [ -d "$SHAREEXT_DIR" ]; then
    # Check if directory has any files (excluding .git and other hidden files)
    if [ "$(find "$SHAREEXT_DIR" -type f -not -path '*/\.*' | wc -l)" -gt 0 ]; then
        SHAREEXT_EXISTS=true
    fi
fi

# Handle fresh install: copy files if ShareExt doesn't exist or is empty
if [ "$SHAREEXT_EXISTS" = false ]; then
    echo -e "${YELLOW}ShareExt directory not found or empty. Setting up initial files...${NC}"
    
    # Create directory
    mkdir -p "$SHAREEXT_DIR"
    
    # Copy files from plugin source
    if [ -d "$PLUGIN_SOURCE_DIR" ]; then
        echo "Copying files from plugin source..."
        cp "$PLUGIN_SOURCE_DIR/ShareViewController.h" "$SHAREEXT_DIR/"
        cp "$PLUGIN_SOURCE_DIR/ShareViewController.m" "$SHAREEXT_DIR/"
        cp "$PLUGIN_SOURCE_DIR/ShareExtension-Info.plist" "$SHAREEXT_DIR/Info.plist"
        cp "$PLUGIN_SOURCE_DIR/ShareExtension.entitlements" "$SHAREEXT_DIR/ShareExt.entitlements"
        
        # Copy storyboard if it exists
        if [ -f "$PLUGIN_SOURCE_DIR/MainInterface.storyboard" ]; then
            mkdir -p "$SHAREEXT_DIR/Base.lproj"
            cp "$PLUGIN_SOURCE_DIR/MainInterface.storyboard" "$SHAREEXT_DIR/Base.lproj/"
            cp "$PLUGIN_SOURCE_DIR/MainInterface.storyboard" "$SHAREEXT_DIR/" 2>/dev/null || true
        fi
        
        echo -e "${GREEN}Files copied successfully.${NC}"
    else
        echo -e "${RED}Error:${NC} Plugin source directory not found: $PLUGIN_SOURCE_DIR"
        exit 1
    fi
fi

# Replace placeholders in ShareExt files (if they exist)
if [ -d "$SHAREEXT_DIR" ]; then
    echo "Checking ShareExt files for placeholders..."
    
    FILES_TO_CHECK=(
        "$SHAREEXT_DIR/ShareViewController.h"
        "$SHAREEXT_DIR/ShareViewController.m"
        "$SHAREEXT_DIR/Info.plist"
        "$SHAREEXT_DIR/ShareExt.entitlements"
    )
    
    for file in "${FILES_TO_CHECK[@]}"; do
        if [ -f "$file" ]; then
            if replace_placeholders "$file" "$BUNDLE_ID" "$URL_SCHEME" "$DISPLAY_NAME" "$VERSION" "$BUILD_NUMBER"; then
                if grep -q "__BUNDLE_IDENTIFIER__\|__URL_SCHEME__\|__DISPLAY_NAME__\|__BUNDLE_SHORT_VERSION_STRING__\|__BUNDLE_VERSION__" "$file" 2>/dev/null; then
                    echo -e "${GREEN}Fixed placeholders in $(basename "$file")${NC}"
                fi
            else
                echo -e "${YELLOW}No placeholders found in $(basename "$file") (already configured)${NC}"
            fi
        fi
    done
fi

# Handle Capacitor copied file
TARGET_FILE="ios/capacitor-cordova-ios-plugins/sources/CordovaPluginShared/ShareViewController.h"

if [ -f "$TARGET_FILE" ]; then
    echo "Checking Capacitor copied file for placeholders..."
    if replace_placeholders "$TARGET_FILE" "$BUNDLE_ID" "$URL_SCHEME" "$DISPLAY_NAME" "$VERSION" "$BUILD_NUMBER"; then
        if grep -q "__BUNDLE_IDENTIFIER__\|__URL_SCHEME__\|__DISPLAY_NAME__\|__BUNDLE_SHORT_VERSION_STRING__\|__BUNDLE_VERSION__" "$TARGET_FILE" 2>/dev/null; then
            echo -e "${GREEN}Fixed placeholders in Capacitor copied file${NC}"
        else
            echo -e "${GREEN}No placeholders found in Capacitor copied file (already fixed)${NC}"
        fi
    fi
else
    echo -e "${YELLOW}Info:${NC} Capacitor copied file not found: $TARGET_FILE"
    echo "This is normal if you haven't run 'npx cap sync' yet."
fi

echo -e "${GREEN}Setup complete!${NC}"
