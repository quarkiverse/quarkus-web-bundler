package io.quarkiverse.web.bundler.deployment.items;

import java.util.List;
import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Import mappings declared by other extensions through
 * {@link io.quarkus.vertx.http.deployment.spi.WebDependencyJarBuildItem}.
 * <p>
 * Like the web-dependency-locator, these modules are served by Quarkus at runtime: they are excluded from
 * the bundling (marked as external) and added to the bundle import map so the browser can resolve them.
 */
public final class WebDependencyImportMappingsBuildItem extends SimpleBuildItem {

    private final Map<String, String> importMappings;

    public WebDependencyImportMappingsBuildItem(Map<String, String> importMappings) {
        this.importMappings = Map.copyOf(importMappings);
    }

    public Map<String, String> importMappings() {
        return importMappings;
    }

    /**
     * @return the esbuild external patterns matching the mapped specifiers (a prefix mapping such as
     *         {@code @scope/lib/} becomes {@code @scope/lib/*})
     */
    public List<String> externals() {
        return importMappings.keySet().stream()
                .map(specifier -> specifier.endsWith("/") ? specifier + "*" : specifier)
                .sorted()
                .toList();
    }
}
