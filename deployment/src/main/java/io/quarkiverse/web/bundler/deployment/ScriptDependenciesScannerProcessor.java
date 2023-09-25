package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.maven.dependency.DependencyFlags;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.paths.PathCollection;

class ScriptDependenciesScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(ScriptDependenciesScannerProcessor.class);

    @BuildStep
    WebDependenciesBuildItem collectDependencies(CurateOutcomeBuildItem curateOutcome,
            WebBundlerConfig config)
            throws IOException {

        Set<Path> webDeps = new HashSet<>();

        Iterable<ResolvedDependency> runDependencies = curateOutcome.getApplicationModel()
                .getDependencies(DependencyFlags.RUNTIME_CP);
        webDeps.addAll(getWebDependencies(runDependencies, config));

        Iterable<ResolvedDependency> compileDependencies = curateOutcome.getApplicationModel()
                .getDependencies(DependencyFlags.COMPILE_ONLY);
        webDeps.addAll(getWebDependencies(compileDependencies, config));

        return new WebDependenciesBuildItem(config.dependencies().type(), List.copyOf(webDeps));
    }

    private Set<Path> getWebDependencies(Iterable<ResolvedDependency> dependencies, WebBundlerConfig config) {
        Stream<ResolvedDependency> dependenciesStream = StreamSupport.stream(dependencies.spliterator(), false);
        return dependenciesStream.filter(Dependency::isJar)
                .filter(config.dependencies().type()::matches)
                .map(ResolvedDependency::getResolvedPaths)
                .flatMap(PathCollection::stream)
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .collect(Collectors.toSet());
    }
}
