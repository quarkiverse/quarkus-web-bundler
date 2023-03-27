package io.quarkiverse.web.assets.deployment.items;

import java.nio.file.Path;
import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

public final class GeneratedBundleBuildItem extends SimpleBuildItem {

    private final Path bundlePath;
    private final Map<String, String> bundle;

    public GeneratedBundleBuildItem(Path bundlePath, Map<String, String> bundle) {
        this.bundlePath = bundlePath;
        this.bundle = bundle;
    }

    public Path getBundlePath() {
        return bundlePath;
    }

    public Map<String, String> getBundle() {
        return bundle;
    }
}
