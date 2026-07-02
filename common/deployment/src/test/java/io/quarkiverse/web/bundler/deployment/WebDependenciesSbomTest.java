package io.quarkiverse.web.bundler.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.mvnpm.esbuild.model.WebDependency;
import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem.Dependency;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.paths.PathList;
import io.quarkus.sbom.ComponentDependencies;
import io.quarkus.sbom.ComponentDescriptor;
import io.quarkus.sbom.Purl;
import io.quarkus.sbom.SbomContribution;

class WebDependenciesSbomTest {

    @Test
    void unscopedPackage() {
        Purl purl = WebDependenciesProcessor.toNpmPurl("org.mvnpm", "jquery", "4.0.0");
        assertEquals("npm", purl.getType());
        assertNull(purl.getNamespace());
        assertEquals("jquery", purl.getName());
        assertEquals("4.0.0", purl.getVersion());
        assertEquals("pkg:npm/jquery@4.0.0", purl.toString());
    }

    @Test
    void scopedPackage() {
        Purl purl = WebDependenciesProcessor.toNpmPurl("org.mvnpm.at.hotwired", "stimulus", "3.2.2");
        assertEquals("npm", purl.getType());
        assertEquals("@hotwired", purl.getNamespace());
        assertEquals("stimulus", purl.getName());
        assertEquals("3.2.2", purl.getVersion());
        assertEquals("pkg:npm/%40hotwired/stimulus@3.2.2", purl.toString());
    }

    @Test
    void scopedPackageWithDifferentNamespace() {
        Purl purl = WebDependenciesProcessor.toNpmPurl("org.mvnpm.at.lit", "reactive-element", "2.0.4");
        assertEquals("@lit", purl.getNamespace());
        assertEquals("reactive-element", purl.getName());
        assertEquals("2.0.4", purl.getVersion());
    }

    @Test
    void nonMvnpmGroupId() {
        Purl purl = WebDependenciesProcessor.toNpmPurl("org.webjars", "bootstrap", "5.3.0");
        assertNull(purl.getNamespace());
        assertEquals("bootstrap", purl.getName());
        assertEquals("5.3.0", purl.getVersion());
    }

    @Test
    void webjarsScopedPackage() {
        Purl purl = WebDependenciesProcessor.toNpmPurl("org.webjars.npm", "types__node", "18.0.0");
        assertEquals("@types", purl.getNamespace());
        assertEquals("node", purl.getName());
        assertEquals("18.0.0", purl.getVersion());
        assertEquals("pkg:npm/%40types/node@18.0.0", purl.toString());
    }

    @Test
    void webjarsUnscopedPackage() {
        Purl purl = WebDependenciesProcessor.toNpmPurl("org.webjars.npm", "htmx.org", "1.9.0");
        assertNull(purl.getNamespace());
        assertEquals("htmx.org", purl.getName());
        assertEquals("1.9.0", purl.getVersion());
    }

    @Test
    void contributionIncludesDependencyRelationships() {
        // bootstrap depends on @popperjs/core
        Dependency bootstrap = dep("org.mvnpm", "bootstrap", "5.3.8",
                List.of(ArtifactCoords.jar("org.mvnpm.at.popperjs", "core", "2.11.8")));
        Dependency popperjs = dep("org.mvnpm.at.popperjs", "core", "2.11.8", List.of());
        Dependency jquery = dep("org.mvnpm", "jquery", "4.0.0", List.of());

        SbomContribution contribution = WebDependenciesProcessor.toSbomContribution(
                List.of(bootstrap, popperjs, jquery));

        // All 3 components present
        Collection<ComponentDescriptor> components = contribution.components();
        assertEquals(3, components.size());

        // Verify dependency relationship: bootstrap -> @popperjs/core
        Collection<ComponentDependencies> deps = contribution.dependencies();
        assertEquals(1, deps.size(), "Only bootstrap should have dependencies");

        ComponentDependencies bootstrapDeps = deps.iterator().next();
        assertEquals("pkg:npm/bootstrap@5.3.8", bootstrapDeps.getBomRef());
        assertEquals(1, bootstrapDeps.getDependsOn().size());
        assertTrue(bootstrapDeps.getDependsOn().contains("pkg:npm/%40popperjs/core@2.11.8"));
    }

    @Test
    void contributionExcludesUnknownDependencies() {
        // bootstrap declares a dependency on @popperjs/core, but it's not in our web deps list
        Dependency bootstrap = dep("org.mvnpm", "bootstrap", "5.3.8",
                List.of(ArtifactCoords.jar("org.mvnpm.at.popperjs", "core", "2.11.8")));

        SbomContribution contribution = WebDependenciesProcessor.toSbomContribution(List.of(bootstrap));

        assertEquals(1, contribution.components().size());
        assertTrue(contribution.dependencies().isEmpty(),
                "No dependency entries when the child is not in the web deps list");
    }

    @Test
    void directDependenciesMarkedTopLevel() {
        // bootstrap is direct, popperjs is transitive (dependency of bootstrap)
        Dependency bootstrap = dep("org.mvnpm", "bootstrap", "5.3.8",
                List.of(ArtifactCoords.jar("org.mvnpm.at.popperjs", "core", "2.11.8")),
                true);
        Dependency popperjs = dep("org.mvnpm.at.popperjs", "core", "2.11.8",
                List.of(), false);
        Dependency jquery = dep("org.mvnpm", "jquery", "4.0.0",
                List.of(), true);

        SbomContribution contribution = WebDependenciesProcessor.toSbomContribution(
                List.of(bootstrap, popperjs, jquery));

        ComponentDescriptor bootstrapComp = contribution.components().stream()
                .filter(c -> "bootstrap".equals(c.getName()))
                .findFirst().orElseThrow();
        assertTrue(bootstrapComp.isTopLevel(), "bootstrap is direct and should be top-level");

        ComponentDescriptor popperjsComp = contribution.components().stream()
                .filter(c -> "core".equals(c.getName()))
                .findFirst().orElseThrow();
        assertFalse(popperjsComp.isTopLevel(), "@popperjs/core is transitive and should not be top-level");

        ComponentDescriptor jqueryComp = contribution.components().stream()
                .filter(c -> "jquery".equals(c.getName()))
                .findFirst().orElseThrow();
        assertTrue(jqueryComp.isTopLevel(), "jquery is direct and should be top-level");
    }

    private static Dependency dep(String groupId, String artifactId, String version,
            List<ArtifactCoords> dependencies) {
        return dep(groupId, artifactId, version, dependencies, true);
    }

    private static Dependency dep(String groupId, String artifactId, String version,
            List<ArtifactCoords> dependencies, boolean direct) {
        var rd = ResolvedDependencyBuilder.newInstance()
                .setGroupId(groupId)
                .setArtifactId(artifactId)
                .setVersion(version)
                .setResolvedPaths(PathList.of(Path.of(artifactId + "-" + version + ".jar")))
                .setDependencies(dependencies)
                .setDirect(direct)
                .build();
        return new Dependency(rd, groupId + ":" + artifactId,
                Path.of(artifactId + "-" + version + ".jar"),
                WebDependency.WebDependencyType.MVNPM, direct);
    }
}
