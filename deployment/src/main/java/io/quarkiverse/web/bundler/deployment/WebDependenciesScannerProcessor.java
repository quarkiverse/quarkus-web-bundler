package io.quarkiverse.web.bundler.deployment;

import static io.mvnpm.esbuild.model.WebDependency.WebDependencyType.resolveType;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.model.WebDependency;
import io.mvnpm.esbuild.model.WebDependency.WebDependencyType;
import io.quarkiverse.web.bundler.deployment.items.EntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.runtime.configuration.ConfigurationException;

class WebDependenciesScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebDependenciesScannerProcessor.class);

    private static final String SCOPE_COMPILE = "compile";
    private static final String SCOPE_PROVIDED = "provided";

    @BuildStep
    WebDependenciesBuildItem collectDependencies(LaunchModeBuildItem launchMode,
            CurateOutcomeBuildItem curateOutcome,
            WebBundlerConfig config,
            List<EntryPointBuildItem> entryPoints) {
        if (entryPoints.isEmpty()) {
            return new WebDependenciesBuildItem(List.of());
        }
        final List<WebDependency> webDeps = StreamSupport.stream(curateOutcome.getApplicationModel()
                .getDependenciesWithAnyFlag(DependencyFlags.COMPILE_ONLY, DependencyFlags.RUNTIME_CP).spliterator(), false)
                .filter(Dependency::isJar)
                .filter(d -> WebDependencyType.anyMatch(d.toCompactCoords()))
                .peek(d -> checkScope(launchMode, d, config))
                .map(WebDependenciesScannerProcessor::toWebDep)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new WebDependenciesBuildItem(webDeps);
    }

    private void checkScope(LaunchModeBuildItem launchMode, ResolvedDependency d, WebBundlerConfig config) {
        if (!launchMode.getLaunchMode().isDevOrTest() && config.dependencies().compileOnly() && d.isRuntimeCp()) {
            throw new ConfigurationException(
                    ("The Web Bundler is configured to only include compileOnly web dependencies, but %s is set as runtime." +
                            " Use a compile only scope (e.g. provided) or set quarkus.web-bundler.dependencies.compile-only=false to allow runtime web dependencies.")
                            .formatted(d.toCompactCoords()));
        }
    }

    private static WebDependency toWebDep(ResolvedDependency d) {
        return d.getResolvedPaths().stream().filter(p -> p.getFileName().toString().endsWith(".jar")).findFirst()
                .map(j -> new WebDependency(d.toCompactCoords(), j, resolveType(d.toCompactCoords()).orElseThrow()))
                .orElse(null);
    }
}
