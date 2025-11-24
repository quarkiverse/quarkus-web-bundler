package io.quarkiverse.web.bundler.deployment.items;

import java.util.Collection;

import io.quarkiverse.web.bundler.deployment.DevWatcher;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.dev.filesystem.watch.FileChangeEvent;

public final class DevWatcherBuildItem extends SimpleBuildItem {

    private final DevWatcher devWatcher;
    private final Collection<FileChangeEvent> previousChanges;

    public DevWatcherBuildItem(DevWatcher devWatcher, Collection<FileChangeEvent> previousChanges) {
        this.devWatcher = devWatcher;
        this.previousChanges = previousChanges;
    }

    public DevWatcher get() {
        return devWatcher;
    }

    public Collection<FileChangeEvent> previousChanges() {
        return previousChanges;
    }

    public boolean detectedAddOrRemoveChanges() {
        return DevWatcher.detectedAddOrRemoveChanges(previousChanges);
    }

    public boolean detectedConfigChange() {
        return previousChanges.stream().anyMatch(c -> {
            final String name = c.getFile().getFileName().toString();
            return name.startsWith("application")
                    && (name.endsWith(".properties") || name.endsWith(".yml") || name.endsWith(".yaml"));
        });
    }

}
