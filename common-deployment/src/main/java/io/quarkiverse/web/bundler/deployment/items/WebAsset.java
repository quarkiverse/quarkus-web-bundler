package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public interface WebAsset {

    String relativePath();

    Optional<String> watchedPath();

    byte[] content();

    Optional<Path> path();

    boolean isSource();

    Charset charset();

    static void watchAssets(BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            List<WebAsset> assets) {
        for (WebAsset asset : assets) {
            watchAsset(watchedFiles, asset, true);
        }
    }

    static void watchAsset(BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            WebAsset asset, boolean restartNeeded) {
        asset.watchedPath().ifPresent(s -> watchedFiles.produce(HotDeploymentWatchedFileBuildItem.builder()
                .setRestartNeeded(restartNeeded)
                .setLocation(s)
                .build()));
    }

    static boolean noneMatch(List<? extends WebAsset> assets, Set<String> changedResources) {
        return assets.stream()
                .map(WebAsset::watchedPath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .noneMatch(changedResources::contains);
    }

    static boolean isLocalFileSystem(Path path) {
        try {
            return "file".equalsIgnoreCase(path.getFileSystem().provider().getScheme());
        } catch (Exception e) {
            return false;
        }
    }

}
