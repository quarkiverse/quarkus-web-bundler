package io.quarkiverse.web.bundler.deployment.items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.quarkus.builder.item.SimpleBuildItem;

public final class WatchedWebDirsBuildItem extends SimpleBuildItem {
    private final List<Path> localWebDirs;
    private final String resourceWebDir;
    private final Collection<Path> srcResourcesDirs;

    public WatchedWebDirsBuildItem(List<Path> localWebDirs, Collection<Path> srcResourcesDirs, String resourceWebDir) {
        this.localWebDirs = localWebDirs;
        this.resourceWebDir = resourceWebDir;
        this.srcResourcesDirs = srcResourcesDirs;
    }

    public List<Path> localWebDirs() {
        return localWebDirs;
    }

    public String resourceWebDir() {
        return resourceWebDir;
    }

    public Collection<Path> srcResourcesDirs() {
        return srcResourcesDirs;
    }

    public List<Path> webDirs() {
        List<Path> webDirs = new ArrayList<>();
        for (Path srcResourcesDir : srcResourcesDirs) {
            final Path resolvedResourceWebDir = srcResourcesDir.resolve(resourceWebDir);
            if (Files.isDirectory(resolvedResourceWebDir)) {
                webDirs.add(resolvedResourceWebDir);
            }
        }
        localWebDirs.forEach(dir -> {
            if (Files.isDirectory(dir)) {
                webDirs.add(dir);
            }
        });
        return webDirs;
    }

    public List<String> webDirsWatchedPaths() {
        List<String> watchedDirs = new ArrayList<>();
        watchedDirs.add(resourceWebDir);
        localWebDirs.forEach(dir -> watchedDirs.add(dir.toString()));
        return watchedDirs;
    }

}
