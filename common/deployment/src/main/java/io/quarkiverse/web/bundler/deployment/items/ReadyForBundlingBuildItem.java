package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.mvnpm.esbuild.model.BundleOptions;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ReadyForBundlingBuildItem extends SimpleBuildItem {

    private final long startTime;
    private final BundleOptions bundleOptions;

    private final Path distDir;

    private final boolean fixedNames;

    public ReadyForBundlingBuildItem(long startTime, BundleOptions bundleOptions, Path distDir, boolean fixedNames) {
        this.startTime = startTime;
        this.bundleOptions = bundleOptions;
        this.distDir = distDir;
        this.fixedNames = fixedNames;
    }

    public long startTime() {
        return startTime;
    }

    public boolean fixedNames() {
        return fixedNames;
    }

    public BundleOptions bundleOptions() {
        return bundleOptions;
    }

    public Path distDir() {
        return distDir;
    }

}
