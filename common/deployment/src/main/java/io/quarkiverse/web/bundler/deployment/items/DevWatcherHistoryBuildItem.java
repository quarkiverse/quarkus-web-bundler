package io.quarkiverse.web.bundler.deployment.items;

import java.util.Collection;

import io.quarkiverse.web.bundler.deployment.DevWatcherProcessor;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.dev.filesystem.watch.FileChangeEvent;

public final class DevWatcherHistoryBuildItem extends SimpleBuildItem {

    private final Collection<FileChangeEvent> previousChanges;

    public DevWatcherHistoryBuildItem(Collection<FileChangeEvent> previousChanges) {
        this.previousChanges = previousChanges;
    }

    public Collection<FileChangeEvent> previousChanges() {
        return previousChanges;
    }

    public boolean detectedAddOrRemoveChanges() {
        return DevWatcherProcessor.detectedAddOrRemoveChanges(previousChanges);
    }

    public boolean detectedConfigChange() {
        return previousChanges.stream().anyMatch(c -> {
            final String name = c.getFile().getFileName().toString();
            return name.startsWith("application")
                    && (name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml"));
        });
    }

}
