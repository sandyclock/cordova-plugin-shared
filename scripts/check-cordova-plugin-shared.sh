#!/bin/bash

# Script to check if placeholder strings exist in cordova-plugin-shared files copied by Capacitor
# This script only checks and warns, it does NOT automatically fix the file
#
# Usage:
#   cp node_modules/cordova-plugin-shared/scripts/check-cordova-plugin-shared.sh scripts/
#   chmod +x scripts/check-cordova-plugin-shared.sh
#   ./scripts/check-cordova-plugin-shared.sh

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
    echo "This script checks for placeholder strings in files copied by Capacitor"
    echo "when using the cordova-plugin-shared plugin."
    echo ""
    echo "You have two options:"
    echo ""
    echo "1. ${GREEN}Install the plugin:${NC}"
    echo "   npm install cordova-plugin-shared"
    echo "   # or"
    echo "   cordova plugin add cordova-plugin-shared"
    echo ""
    echo "2. ${YELLOW}Remove this script from package.json${NC} (if you don't need the plugin):"
    echo "   Remove the following line from package.json (if present):"
    echo "   \"check:shared\": \"./scripts/check-cordova-plugin-shared.sh\""
    echo ""
    exit 1
fi

# Path to the file that Capacitor copies
# This path is relative to the project root (where package.json is located)
TARGET_FILE="ios/capacitor-cordova-ios-plugins/sources/CordovaPluginShared/ShareViewController.h"

# Check if file exists
if [ ! -f "$TARGET_FILE" ]; then
    echo -e "${YELLOW}Info:${NC} File not found: $TARGET_FILE"
    echo "This is normal if you haven't run 'npx cap sync' yet or if the plugin isn't installed."
    exit 0
fi

# Check if file contains placeholders
if grep -q "__BUNDLE_IDENTIFIER__\|__URL_SCHEME__" "$TARGET_FILE"; then
    echo -e "${RED}WARNING:${NC} Placeholders found in $TARGET_FILE"
    echo ""
    echo "The following placeholders need to be replaced:"
    grep "__BUNDLE_IDENTIFIER__\|__URL_SCHEME__" "$TARGET_FILE" | sed 's/^/  /'
    echo ""
    echo "To fix automatically, run:"
    echo "  ./scripts/fix-cordova-plugin-shared.sh"
    echo ""
    echo "Or fix manually by editing:"
    echo "  $TARGET_FILE"
    echo ""
    echo "Replace:"
    echo "  __BUNDLE_IDENTIFIER__ with your share extension bundle ID (e.g., com.example.myapp.shareextension)"
    echo "  __URL_SCHEME__ with your URL scheme (e.g., myapp)"
    exit 1
else
    echo -e "${GREEN}OK:${NC} No placeholders found in $TARGET_FILE"
    exit 0
fi

