package io.quarkiverse.web.bundler.deployment;

import static io.mvnpm.esbuild.model.WebDependency.WebDependencyType.resolveType;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.model.WebDependency;
import io.mvnpm.esbuild.model.WebDependency.WebDependencyType;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.ResolvedDependency;

class WebDependenciesScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebDependenciesScannerProcessor.class);

    @BuildStep
    WebDependenciesBuildItem collectDependencies(CurateOutcomeBuildItem curateOutcome, WebBundlerConfig config) {
        List<WebDependency> webDeps = curateOutcome.getApplicationModel().getRuntimeDependencies().stream()
                .filter(Dependency::isJar)
                .filter(d -> WebDependencyType.anyMatch(d.toCompactCoords()))
                .map(WebDependenciesScannerProcessor::toWebDep)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new WebDependenciesBuildItem(webDeps);
    }

    private static WebDependency toWebDep(ResolvedDependency d) {
        return d.getResolvedPaths().stream().filter(p -> p.getFileName().toString().endsWith(".jar")).findFirst()
                .map(j -> new WebDependency(d.toCompactCoords(), j, resolveType(d.toCompactCoords()).orElseThrow()))
                .orElse(null);
    }
}
