# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Quarkus Web Bundler is a Quarkus extension for full-stack web apps. It provides zero-configuration bundling and minification (via esbuild) for JS/TS/JSX/TSX, CSS/SCSS/SASS, and npm dependencies (via mvnpm or webjars). It integrates with Quarkus Qute for server-side templating and supports plugins like Svelte and TailwindCSS.

## Build Commands

```bash
# Full build (skips docs and integration tests)
mvn install -DskipTests

# Full build with integration tests
mvn install

# Run unit tests in core/deployment (uses QuarkusUnitTest with ShrinkWrap)
mvn verify -pl core/deployment

# Run a single integration test module
mvn verify -pl integration-tests/core

# Run a specific test class
mvn verify -pl integration-tests/core -Dtest=BundleTest

# Build the scanner/string-paths tools (independent parent POM)
mvn install -f tools/pom.xml

# Build docs
mvn install -Pdocs
```

Java 17+ required. No Maven wrapper is included; use system Maven.

## Module Structure

The project has two independent Maven trees (different parent POMs):

**`tools/`** (parent: `quarkiverse-parent`, groupId: `io.quarkiverse.tools`) - Reusable libraries shared with other Quarkiverse extensions:
- `scanner/` - Project resource scanning library (`ProjectScanner`, `ScanQuery`, `ProjectFile`)
- `string-paths/` - Path string manipulation utilities (`StringPaths`)

**Root** (parent: `quarkiverse-parent`, groupId: `io.quarkiverse.web-bundler`) - The extension itself:
- `common/deployment/` - Core build-time processors (scanning, bundling, serving)
- `common/runtime/` - Runtime classes (`Bundle` bean, config, recorders, live-reload handler)
- `core/spi/` - Service Provider Interface for extension plugins (`WebBundlerWatchedDirBuildItem`)
- `core/deployment/` - Unit tests using `QuarkusUnitTest` (being migrated to `common/`)
- `core/runtime/` - (Being migrated to `common/`)
- `integrations/svelte/` - Svelte esbuild plugin integration
- `integrations/tailwindcss/` - TailwindCSS esbuild plugin integration
- `integrations/qute-components/` - Qute web components (template + script + style)
- `integration-tests/core/` - Integration tests using `@QuarkusTest` + RestAssured
- `integration-tests/core/locker/` - POM-only module for locking web dependency versions
- `integration-tests/svelte/`, `integration-tests/tailwindcss/`

Each extension module follows the Quarkus pattern: `deployment/` for build-time logic (`@BuildStep` processors) and `runtime/` for runtime code (recorders, beans, config).

## Architecture: Bundling Pipeline

The build pipeline executes as Quarkus build steps. The key processors are in `common/deployment/`:

1. **Init** - `WebBundlerInitProcessor` declares scan config and sets up dev-mode file watching.

2. **Scanning** - `ProjectScannerProcessor` (in `tools/scanner/`) builds an index of all web resources (from JARs, source dirs, local project dirs), shared via `ProjectScannerBuildItem`.

3. **Asset Collection** - Three scanner processors collect assets:
   - `BundleWebAssetsScannerProcessor` -> `EntryPointBuildItem` (bundle entry points from `web/<entrypoint>/`)
   - `StaticAssetsScannerProcessor` -> `PublicAssetsBuildItem` (from `web/public/` and `web/static/`)
   - `QuteTemplateAssetsScannerProcessor` -> `QuteTemplatesBuildItem` (`.html` files in `web/`)

4. **Dependency Installation** - `WebDependenciesProcessor` collects mvnpm/webjar dependencies and installs them into `node_modules/`.

5. **Bundle Preparation** - `BundlePrepareProcessor` creates esbuild configuration (`BundleOptions`): loaders, source maps, splitting, plugins, entry points -> `ReadyForBundlingBuildItem`.

6. **Bundle Execution** - `BundleProcessor` (prod) or `DevBundleProcessor` (dev) calls esbuild-java to bundle -> `GeneratedBundleBuildItem`.

7. **Resource Serving** - `GeneratedWebResourcesProcessor` registers bundled files as Quarkus static resources. `StaticWebAssetsProcessor` and `QuteTemplateWebAssetsProcessor` handle their respective asset types. In dev mode, SSE live-reload is available at `/web-bundler/live`.

## Key Conventions

- **Configuration prefix**: `quarkus.web-bundler` (see `WebBundlerConfig`), `quarkus.project-scanner` (see `ProjectScannerConfig`)
- **Default web root**: `src/main/resources/web/` with entry points in `web/`, static assets in `web/public/` and `web/static/`
- **esbuild-java**: The bundling engine, also maintained by the Quarkus team (version in root `pom.xml` property `esbuild-java.version`)
- **Build items**: `SimpleBuildItem` for singletons, `MultiBuildItem` for collections

### Plugin SPI Pattern

Integration plugins contribute esbuild plugins via `WebBundlerEsbuildPluginBuiltItem`. A plugin processor is minimal: produce a `FeatureBuildItem` and a `WebBundlerEsbuildPluginBuiltItem` wrapping an `EsBuildPlugin` instance. See `WebBundlerSvelteProcessor` for the canonical example.

### Test Patterns

Two test styles are used:

- **Deployment tests** (`core/deployment/src/test/`): Use `QuarkusUnitTest` with `@RegisterExtension` and ShrinkWrap archives. Resources are added via `.addAsResource("web")`. These test the build pipeline in isolation. `QuarkusDevModeTest` tests hot-reload behavior.
- **Integration tests** (`integration-tests/`): Use `@QuarkusTest` with RestAssured HTTP assertions and an injected `Bundle` bean to verify bundle mapping and resource serving.

### Scanner Conventions

- `indexPath` is a scanner-internal concept only, used within `tools/scanner/` code
- Outside the scanner (e.g., `common/deployment/`), use `relativePath` since paths are typically converted to it
