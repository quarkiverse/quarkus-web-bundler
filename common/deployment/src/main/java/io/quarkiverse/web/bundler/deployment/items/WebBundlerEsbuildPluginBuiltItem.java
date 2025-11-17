package io.quarkiverse.web.bundler.deployment.items;

import io.mvnpm.esbuild.model.EsBuildPlugin;
import io.quarkus.builder.item.MultiBuildItem;

public final class WebBundlerEsbuildPluginBuiltItem extends MultiBuildItem {
    private final EsBuildPlugin plugin;

    public WebBundlerEsbuildPluginBuiltItem(EsBuildPlugin plugin) {
        this.plugin = plugin;
    }

    public EsBuildPlugin get() {
        return plugin;
    }

    @Override
    public String toString() {
        return plugin.name();
    }
}
