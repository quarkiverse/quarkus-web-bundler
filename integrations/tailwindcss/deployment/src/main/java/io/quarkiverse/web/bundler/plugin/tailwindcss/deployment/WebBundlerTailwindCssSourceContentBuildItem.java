package io.quarkiverse.web.bundler.plugin.tailwindcss.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class WebBundlerTailwindCssSourceContentBuildItem extends MultiBuildItem {
    private final String content;

    public WebBundlerTailwindCssSourceContentBuildItem(String content) {
        this.content = content;
    }

    public String content() {
        return content;
    }
}
