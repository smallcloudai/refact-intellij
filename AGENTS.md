# Refact IntelliJ Plugin - Development Guide

## Build Requirements

- **Java**: JDK 17+ (use JetBrains JBR for best compatibility)
- **Gradle**: 9.2.1 (wrapper included)

## Building with JetBrains JBR

```bash
# Find JBR path
JAVA_HOME=~/.local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/jbr

# Build (skip tests)
JAVA_HOME=$JAVA_HOME ./gradlew build -x test

# Build plugin distribution
JAVA_HOME=$JAVA_HOME ./gradlew buildPlugin

# Run IDE with plugin
JAVA_HOME=$JAVA_HOME ./gradlew runIde
```

## Project Structure

- `src/main/kotlin/com/smallcloud/refactai/` - Main source code
- `src/main/resources/META-INF/plugin.xml` - Plugin configuration
- `gradle.properties` - Build configuration and dependencies

## Key Configuration (gradle.properties)

- `platformVersion` - Target IntelliJ version
- `platformBundledPlugins` - Required bundled plugins (e.g., Git4Idea)
- `pluginSinceBuild` / `pluginUntilBuild` - Compatibility range

## Updating Dependencies

Use the provided script to update React UI and LSP binary:

```bash
# Update everything (UI + LSP + build)
./update-dependencies.sh

# Update only React UI
./update-dependencies.sh --ui-only

# Update only LSP binary
./update-dependencies.sh --lsp-only

# Use debug LSP build (faster compilation, larger binary)
./update-dependencies.sh --debug-lsp

# Update without rebuilding plugin
./update-dependencies.sh --no-build
```

The script automatically:
- Pulls latest changes from refact-chat-js and refact-agent
- Builds React UI and copies to `src/main/resources/webview/dist/`
- Builds LSP binary and copies to `src/main/resources/bin/`
- Rebuilds the plugin with updated dependencies

## LSP Integration

The plugin communicates with refact-lsp via HTTP endpoints:
- `/v1/caps` - Capabilities
- `/v1/rag-status` - RAG indexing status
- `/v1/commit-message-from-diff` - Generate commit messages

### LSP Binary Location

- Source: `~/projects/smc/refact/refact-agent/engine`
- Bundled in: `src/main/resources/bin/dist-x86_64-unknown-linux-gnu/refact-lsp`
- Runtime: Extracted to temp directory with MD5 hash in filename
