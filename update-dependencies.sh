#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REFACT_AGENT_DIR="$HOME/projects/smc/refact/refact-agent"
GUI_DIR="$REFACT_AGENT_DIR/gui"
LSP_BINARY="$REFACT_AGENT_DIR/engine/target/debug/refact-lsp"

WEBVIEW_DIST_DIR="$SCRIPT_DIR/src/main/resources/webview/dist"
BIN_DIR="$SCRIPT_DIR/src/main/resources/bin"

echo "=== Building GUI ==="
cd "$GUI_DIR"
npm install
npm run build

echo "=== Copying GUI dist ==="
rm -rf "$WEBVIEW_DIST_DIR"
mkdir -p "$WEBVIEW_DIST_DIR"
cp -r "$GUI_DIR/dist/"* "$WEBVIEW_DIST_DIR/"

echo "=== Copying LSP binary ==="
mkdir -p "$BIN_DIR/dist-x86_64-unknown-linux-gnu"
cp "$LSP_BINARY" "$BIN_DIR/dist-x86_64-unknown-linux-gnu/refact-lsp"
chmod +x "$BIN_DIR/dist-x86_64-unknown-linux-gnu/refact-lsp"

echo "=== Done ==="
