package io.quarkiverse.web.bundler.deployment;

import static io.mvnpm.esbuild.model.WebDependency.WebDependencyType.resolveType;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.install.WebDepsInstaller;
import io.mvnpm.esbuild.model.BundleOptions;
import io.mvnpm.esbuild.model.WebDependency;
import io.mvnpm.esbuild.model.WebDependency.WebDependencyType;
import io.quarkiverse.tools.projectscanner.ProjectRootBuildItem;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.InstalledWebDependenciesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem.Dependency;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.runtime.configuration.ConfigurationException;

class WebDependenciesProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebDependenciesProcessor.class);

    @BuildStep
    WebDependenciesBuildItem collectDependencies(LaunchModeBuildItem launchMode,
            CurateOutcomeBuildItem curateOutcome,
            List<EntryPointBuildItem> entryPoints,
            WebBundlerConfig config) {
        if (entryPoints.isEmpty() && !config.dependencies().autoImport().isEnabled()) {
            return new WebDependenciesBuildItem(List.of());
        }
        final List<Dependency> buildDependencies = StreamSupport.stream(curateOutcome.getApplicationModel()
                .getDependencies().spliterator(), false)
                .filter(io.quarkus.maven.dependency.Dependency::isJar)
                .filter(d -> WebDependencyType.MVNPM.matches(d.toCompactCoords()))
                .filter(d -> d.isDeploymentCp() && ("compile".equals(d.getScope()) || "test".equals(d.getScope())))
                .map(WebDependenciesProcessor::toWebDep)
                .filter(Objects::nonNull)
                .toList();
        final List<Dependency> runtimeDependencies = StreamSupport.stream(curateOutcome.getApplicationModel()
                .getDependenciesWithAnyFlag(DependencyFlags.COMPILE_ONLY, DependencyFlags.RUNTIME_CP).spliterator(), false)
                .filter(io.quarkus.maven.dependency.Dependency::isJar)
                .filter(d -> WebDependencyType.anyMatch(d.toCompactCoords()))
                .peek(d -> checkScope(launchMode, d, config))
                .map(WebDependenciesProcessor::toWebDep)
                .filter(Objects::nonNull)
                .toList();
        // Discover Quarkus extension JARs that ship META-INF/importmap.json (e.g. extensions with JS assets)
        final List<Dependency> importMapDependencies = StreamSupport.stream(curateOutcome.getApplicationModel()
                .getDependencies(DependencyFlags.RUNTIME_EXTENSION_ARTIFACT).spliterator(), false)
                .filter(io.quarkus.maven.dependency.Dependency::isJar)
                .filter(d -> !WebDependencyType.anyMatch(d.toCompactCoords()))
                .filter(WebDependenciesProcessor::hasImportMap)
                .map(d -> toWebDep(d, WebDependencyType.MVNPM))
                .filter(Objects::nonNull)
                .toList();
        if (!importMapDependencies.isEmpty()) {
            LOGGER.debugf("Discovered %d web dependencies via META-INF/importmap.json: %s",
                    importMapDependencies.size(),
                    importMapDependencies.stream().map(Dependency::id).collect(Collectors.joining(", ")));
        }
        final List<Dependency> dependencies = new ArrayList<>(
                buildDependencies.size() + runtimeDependencies.size() + importMapDependencies.size());
        dependencies.addAll(buildDependencies);
        dependencies.addAll(runtimeDependencies);
        dependencies.addAll(importMapDependencies);
        return new WebDependenciesBuildItem(dependencies);
    }

    @BuildStep
    InstalledWebDependenciesBuildItem installDependencies(LaunchModeBuildItem launchMode,
            LiveReloadBuildItem liveReload,
            OutputTargetBuildItem outputTarget,
            ProjectRootBuildItem projectRoot,
            WebDependenciesBuildItem webDependencies,
            WebBundlerConfig config) {
        if (!projectRoot.exists()) {
            return null;
        }
        final Path nodeModulesDir = resolveNodeModulesDir(config, outputTarget, projectRoot);
        final Path relativeNodeModulesDir = projectRoot.path().relativize(nodeModulesDir);
        long startedInstall = Instant.now().toEpochMilli();
        try {
            final List<WebDependency> toInstall = webDependencies.toEsBuildWebDependencies();
            if (WebDepsInstaller.install(nodeModulesDir, toInstall)) {
                final long duration = Instant.now().minusMillis(startedInstall).toEpochMilli();
                if (LOGGER.isDebugEnabled()) {
                    String deps = webDependencies.list().stream().map(Dependency::id)
                            .collect(
                                    Collectors.joining(", "));

                    LOGGER.infof("%d Web Dependencies installed in node_modules in './%s' (%sms): %s",
                            webDependencies.list().size(),
                            relativeNodeModulesDir,
                            duration, deps);
                } else {
                    LOGGER.infof("%d Web Dependencies installed in './%s' (%sms).",
                            webDependencies.list().size(),
                            relativeNodeModulesDir,
                            duration);
                }
            } else if (webDependencies.isEmpty()) {
                LOGGER.infof("No Web Dependencies to install in './%s'.", relativeNodeModulesDir);
            } else {
                LOGGER.infof("All Web Dependencies are already installed in './%s'.", relativeNodeModulesDir);
            }
            liveReload.setContextObject(InstalledWebDependenciesContext.class,
                    new InstalledWebDependenciesContext(config, nodeModulesDir, webDependencies.list()));
            return new InstalledWebDependenciesBuildItem(nodeModulesDir, webDependencies.list());
        } catch (IOException e) {
            liveReload.setContextObject(InstalledWebDependenciesContext.class, new InstalledWebDependenciesContext());
            throw new RuntimeException(e);
        }
    }

    private void checkScope(LaunchModeBuildItem launchMode, ResolvedDependency d, WebBundlerConfig config) {
        if (!launchMode.getLaunchMode().isDevOrTest() && config.dependencies().compileOnly() && d.isRuntimeCp()) {
            throw new ConfigurationException(
                    ("The Web Bundler is configured to only include compileOnly web dependencies, but %s is set as runtime." +
                            " Use a compile only scope (e.g. provided) or set quarkus.web-bundler.dependencies.compile-only=false to allow runtime web dependencies.")
                            .formatted(d.toCompactCoords()));
        }
    }

    private static Dependency toWebDep(ResolvedDependency d) {
        return toWebDep(d, resolveType(d.toCompactCoords()).orElseThrow());
    }

    private static Dependency toWebDep(ResolvedDependency d, WebDependencyType type) {
        return d.getResolvedPaths().stream().filter(p -> p.getFileName().toString().endsWith(".jar")).findFirst()
                .map(j -> new Dependency(d, d.toCompactCoords(), j, type, d.isDirect()))
                .orElse(null);
    }

    private static boolean hasImportMap(ResolvedDependency d) {
        return d.getResolvedPaths().stream()
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .findFirst()
                .map(WebDependenciesProcessor::jarContainsImportMap)
                .orElse(false);
    }

    private static boolean jarContainsImportMap(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return jarFile.getEntry("META-INF/importmap.json") != null;
        } catch (IOException e) {
            LOGGER.debugf("Could not inspect JAR for importmap.json: %s", jarPath);
            return false;
        }
    }

    private static Path resolveNodeModulesDir(WebBundlerConfig config, OutputTargetBuildItem outputTarget,
            ProjectRootBuildItem projectRoot) {
        if (config.nodeModules().isEmpty()) {
            final Path gitIgnore = projectRoot.path().resolve(".gitignore");
            if (Files.exists(gitIgnore) && !checkGitIgnoreNodeModules(gitIgnore)) {
                LOGGER.warnf("The .gitignore file does not ignore the Web Bundler node_modules directory.");
            }
            return projectRoot.path().resolve(BundleOptions.NODE_MODULES);
        }
        final Path nodeModulesDir = Path.of(config.nodeModules().get().trim()).resolve(BundleOptions.NODE_MODULES);
        if (nodeModulesDir.isAbsolute() && Files.isDirectory(nodeModulesDir)) {
            return nodeModulesDir;
        }
        if (!projectRoot.exists() || !Files.isDirectory(projectRoot.path())) {
            throw new IllegalStateException(
                    "If not absolute, the node_modules directory is resolved relative to the project root, but Web Bundler was not able to find the project root.");
        }
        return projectRoot.path().resolve(nodeModulesDir);
    }

    record InstalledWebDependenciesContext(WebBundlerConfig config, Path nodeModulesDir, List<Dependency> dependencies) {
        InstalledWebDependenciesContext() {
            this(null, null, List.of());
        }
    }

    public static boolean checkGitIgnoreNodeModules(Path gitignore) {
        try (BufferedReader reader = Files.newBufferedReader(gitignore)) {
            String line;
            while ((line = reader.readLine()) != null) {

                // Trim whitespace
                line = line.trim();
                if (line.isEmpty())
                    continue;

                // Skip comments
                if (line.startsWith("#"))
                    continue;

                // Normalize trailing slash
                if (line.endsWith("/")) {
                    line = line.substring(0, line.length() - 1);
                }

                // Exact node_modules or recursive match
                if (line.equals("node_modules")) {
                    return true;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return false;
    }

}
