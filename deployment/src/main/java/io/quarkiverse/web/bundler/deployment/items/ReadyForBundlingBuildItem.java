package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.mvnpm.esbuild.model.BundleOptions;
import io.quarkus.builder.item.SimpleBuildItem;

public final class ReadyForBundlingBuildItem extends SimpleBuildItem {

    private final BundleOptions bundleOptions;

    private final Long started;

    private final Path distDir;

    private final boolean fixedNames;

    private final boolean useEsbuildWatch;

    public ReadyForBundlingBuildItem(BundleOptions bundleOptions, Long started, Path distDir, boolean fixedNames,
            boolean useEsbuildWatch) {
        this.bundleOptions = bundleOptions;
        this.started = started;
        this.distDir = distDir;
        this.fixedNames = fixedNames;
        this.useEsbuildWatch = useEsbuildWatch;
    }

    public boolean fixedNames() {
        return fixedNames;
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

    public boolean useEsbuildWatch() {
        return useEsbuildWatch;
    }
}
