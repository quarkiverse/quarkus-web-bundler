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

# Run a single integration test module
mvn verify -pl integration-tests/core

# Run a specific test class
mvn verify -pl integration-tests/core -Dtest=BundleTest

# Build docs
mvn install -Pdocs

# Install Playwright browsers (needed for browser-based tests)
mvn quarkus:dev -Pplaywright-cli
```

Java 17+ required. No Maven wrapper is included; use system Maven.

## Module Structure

```
scanner/                    # Reusable project resource scanning library
common/
  deployment/               # Core build-time processors (scanning, bundling, serving)
  runtime/                  # Runtime classes (Bundle bean, config, live-reload handler)
core/
  spi/                      # Service Provider Interface for extension plugins
  runtime/                  # (Being migrated to common/)
  deployment/               # (Being migrated to common/)
integrations/
  svelte/                   # Svelte esbuild plugin integration
  tailwindcss/              # TailwindCSS esbuild plugin integration
  qute-components/          # Qute web components (template + script + style)
integration-tests/
  core/                     # Core bundler tests
  svelte/                   # Svelte integration tests
  tailwindcss/              # TailwindCSS integration tests
```

Each extension module follows the Quarkus pattern: `deployment/` for build-time logic (`@BuildStep` processors) and `runtime/` for runtime code (recorders, beans, config).

## Architecture: Bundling Pipeline

The build pipeline executes as Quarkus build steps in this order:

1. **Scanning** - `ProjectScannerProcessor` builds an index of all web resources (from JARs, source dirs, local project dirs). The scanner is shared via `ProjectScannerBuildItem` and queried by subsequent processors.

2. **Asset Collection** - Three scanner processors collect assets:
   - `BundleWebAssetsScannerProcessor` → `EntryPointBuildItem` (bundle entry points from `web/<entrypoint>/`)
   - `StaticAssetsScannerProcessor` → `PublicAssetsBuildItem` (from `web/public/` and `web/static/`)
   - `QuteTemplateAssetsScannerProcessor` → `QuteTemplatesBuildItem` (`.html` files in `web/`)

3. **Dependency Installation** - `WebDependenciesProcessor` collects mvnpm/webjar dependencies and installs them into `node_modules/`.

4. **Bundle Preparation** - `BundlePrepareProcessor` creates esbuild configuration (`BundleOptions`): loaders, source maps, splitting, plugins, entry points → `ReadyForBundlingBuildItem`.

5. **Bundle Execution** - `BundleProcessor` (prod) or `DevBundleProcessor` (dev) calls esbuild-java to bundle → `GeneratedBundleBuildItem`.

6. **Resource Serving** - `GeneratedWebResourcesProcessor` registers bundled files as Quarkus static resources. In dev mode, sets up SSE live-reload at `/web-bundler/live`.

## Key Conventions

- **Configuration prefix**: `quarkus.web-bundler` (see `WebBundlerConfig`), `quarkus.project-scanner` (see `ProjectScannerConfig`)
- **Default web root**: `src/main/resources/web/` — entry points in `web/`, static assets in `web/public/`
- **esbuild-java**: The bundling engine that is also maintained by the quarkus team (version in root pom.xml property `esbuild-java.version`)
- **Plugin pattern**: Integrations contribute esbuild plugins via `WebBundlerEsbuildPluginBuiltItem`
- **Build items**: `SimpleBuildItem` for singletons, `MultiBuildItem` for collections
- **Tests**: Use `@QuarkusTest` + RestAssured for HTTP assertions + injected `Bundle` bean for mapping verification
- **Scanner branch**: Active refactoring to extract scanner into a shared reusable module
