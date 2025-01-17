package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.logging.Logger;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public final class Watchers {
    public static final Logger LOGGER = Logger.getLogger(Watchers.class);

    public static void watchDirectory(Path dir, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.forEach(f -> {
                if (Files.isRegularFile(f)) {
                    return;
                }
                watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocation(f.toAbsolutePath().toString())
                        .setRestartNeeded(false).build());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("Watching %s for changes", f);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read directory: %s".formatted(dir), e);
        }
    }

    public static void watchResourceDir(BuildProducer<HotDeploymentWatchedFileBuildItem> watch, String dir) {
        watch.produce(HotDeploymentWatchedFileBuildItem.builder().setLocationPredicate(p -> p.startsWith(dir))
                .setRestartNeeded(true).build());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Watching resources %s for changes", dir);
        }
    }

}
