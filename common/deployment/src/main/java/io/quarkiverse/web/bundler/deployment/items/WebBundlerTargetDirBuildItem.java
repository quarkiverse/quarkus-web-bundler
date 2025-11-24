package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public final class WebBundlerTargetDirBuildItem extends SimpleBuildItem {

    private final Path targetDir;
    private final Path distDir;
    private final boolean keepDir;

    public WebBundlerTargetDirBuildItem(Path targetDir, Path distDir, boolean keepDir) {
        this.targetDir = targetDir;
        this.distDir = distDir;
        this.keepDir = keepDir;
    }

    public boolean keepDir() {
        return keepDir;
    }

    public Path webBundler() {
        return targetDir;
    }

    public Path dist() {
        return distDir;
    }
}
