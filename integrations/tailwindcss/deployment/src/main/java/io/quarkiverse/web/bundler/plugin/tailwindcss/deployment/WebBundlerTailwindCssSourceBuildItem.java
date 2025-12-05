package io.quarkiverse.web.bundler.plugin.tailwindcss.deployment;

import io.mvnpm.esbuild.plugin.EsBuildPluginTailwind;
import io.quarkus.builder.item.MultiBuildItem;

public final class WebBundlerTailwindCssSourceBuildItem extends MultiBuildItem {
    private final EsBuildPluginTailwind.Source source;

    public WebBundlerTailwindCssSourceBuildItem(EsBuildPluginTailwind.Source source) {
        this.source = source;
    }

    public WebBundlerTailwindCssSourceBuildItem(String path, String pattern, boolean negated) {
        this(new EsBuildPluginTailwind.Source(path, pattern, negated));
    }

    public EsBuildPluginTailwind.Source source() {
        return source;
    }
}
