package io.quarkiverse.web.bundler.deployment.items;

import io.quarkiverse.web.bundler.deployment.watcher.DevWatcher;
import io.quarkus.builder.item.SimpleBuildItem;

public final class DevWatcherBuildItem extends SimpleBuildItem {

    private final DevWatcher devWatcher;

    public DevWatcherBuildItem(DevWatcher devWatcher) {
        this.devWatcher = devWatcher;
    }

    public DevWatcher get() {
        return devWatcher;
    }
}
