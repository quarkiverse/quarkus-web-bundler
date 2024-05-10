package io.quarkiverse.web.bundler.deployment.items;

import io.mvnpm.esbuild.model.BundleOptions;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ReadyForBundlingBuildItem extends SimpleBuildItem {

    private final BundleOptions bundleOptions;

    private final Long started;

    public ReadyForBundlingBuildItem(BundleOptions bundleOptions, Long started) {
        this.bundleOptions = bundleOptions;
        this.started = started;
    }

    public Long started() {
        return started;
    }

    public BundleOptions bundleOptions() {
        return bundleOptions;
    }
}
