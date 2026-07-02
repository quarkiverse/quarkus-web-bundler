package io.quarkiverse.web.bundler.deployment;

import static io.mvnpm.esbuild.model.WebDependency.WebDependencyType.resolveType;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.sbom.SbomContributionBuildItem;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.sbom.ComponentDependencies;
import io.quarkus.sbom.ComponentDescriptor;
import io.quarkus.sbom.Purl;
import io.quarkus.sbom.SbomContribution;

class WebDependenciesProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebDependenciesProcessor.class);
    private static final String ORG_MVNPM_AT = "org.mvnpm.at.";

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
        final List<Dependency> dependencies = new ArrayList<>(buildDependencies.size() + runtimeDependencies.size());
        dependencies.addAll(buildDependencies);
        dependencies.addAll(runtimeDependencies);
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

    @BuildStep
    void produceSbomContribution(
            InstalledWebDependenciesBuildItem installed,
            BuildProducer<SbomContributionBuildItem> sbomProducer) {
        if (installed == null || installed.isEmpty()) {
            return;
        }
        final SbomContribution contribution = toSbomContribution(installed.list());
        if (!contribution.components().isEmpty()) {
            sbomProducer.produce(new SbomContributionBuildItem(contribution));
        }
    }

    static SbomContribution toSbomContribution(List<Dependency> deps) {
        // Build components and index by Maven artifact key for dependency resolution
        final List<ComponentDescriptor> components = new ArrayList<>(deps.size());
        final Map<ArtifactKey, String> mavenKeyToBomRef = new HashMap<>(deps.size());
        for (Dependency dep : deps) {
            final ResolvedDependency rd = dep.resolvedDependency();
            final ComponentDescriptor descriptor = ComponentDescriptor.builder()
                    .setPurl(toNpmPurl(rd.getGroupId(), rd.getArtifactId(), rd.getVersion()))
                    .setTopLevel(rd.isDirect())
                    .build();
            components.add(descriptor);
            mavenKeyToBomRef.put(rd.getKey(), descriptor.getBomRef());
        }

        // Resolve dependency relationships from the Maven model
        final List<ComponentDependencies> dependencies = new ArrayList<>();
        for (Dependency dep : deps) {
            final ResolvedDependency rd = dep.resolvedDependency();
            final String parentBomRef = mavenKeyToBomRef.get(rd.getKey());
            List<String> dependsOn = null;
            for (ArtifactCoords child : rd.getDependencies()) {
                final String childBomRef = mavenKeyToBomRef.get(child.getKey());
                if (childBomRef != null) {
                    if (dependsOn == null) {
                        dependsOn = new ArrayList<>();
                    }
                    dependsOn.add(childBomRef);
                }
            }
            if (dependsOn != null) {
                dependencies.add(ComponentDependencies.of(parentBomRef, dependsOn));
            }
        }

        return SbomContribution.of(components, dependencies);
    }

    static Purl toNpmPurl(String groupId, String artifactId, String version) {
        String npmNamespace = null;
        String npmName = artifactId;
        if (groupId.startsWith(ORG_MVNPM_AT)) {
            npmNamespace = "@" + groupId.substring(ORG_MVNPM_AT.length());
        } else if ("org.webjars.npm".equals(groupId)) {
            int scopeSep = artifactId.indexOf("__");
            if (scopeSep > 0) {
                npmNamespace = "@" + artifactId.substring(0, scopeSep);
                npmName = artifactId.substring(scopeSep + 2);
            }
        }
        return Purl.npm(npmNamespace, npmName, version);
    }

    private static Dependency toWebDep(ResolvedDependency d) {
        return d.getResolvedPaths().stream().filter(p -> p.getFileName().toString().endsWith(".jar")).findFirst()
                .map(j -> new Dependency(d, d.toCompactCoords(), j, resolveType(d.toCompactCoords()).orElseThrow(),
                        d.isDirect()))
                .orElse(null);
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
