package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public final class WebBundlerTargetDirBuildItem extends SimpleBuildItem {

    private final Path targetDir;
    private final Path distDir;

    public WebBundlerTargetDirBuildItem(Path targetDir, Path distDir) {
        this.targetDir = targetDir;
        this.distDir = distDir;
    }

    public Path webBundler() {
        return targetDir;
    }

    public Path dist() {
        return distDir;
    }
}
