package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.mvnpm.esbuild.model.BundleOptions;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ReadyForBundlingBuildItem extends SimpleBuildItem {

    private final BundleOptions bundleOptions;

    private final Long started;

    private final Path distDir;

    private final boolean disableBundlingWatch;

    public ReadyForBundlingBuildItem(BundleOptions bundleOptions, Long started, Path distDir, boolean disableBundlingWatch) {
        this.bundleOptions = bundleOptions;
        this.started = started;
        this.distDir = distDir;
        this.disableBundlingWatch = disableBundlingWatch;
    }

    public Long started() {
        return started;
    }

    public BundleOptions bundleOptions() {
        return bundleOptions;
    }

    public Path distDir() {
        return distDir;
    }

    public boolean enabledBundlingWatch() {
        return disableBundlingWatch;
    }
}
