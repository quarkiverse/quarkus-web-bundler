package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.DevWatchedLinkBuildItem;
import io.quarkiverse.web.bundler.deployment.items.DevWatcherHistoryBuildItem;
import io.quarkiverse.web.bundler.deployment.items.DevWatcherStartedBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebDirBuildItem;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.dev.RuntimeUpdatesProcessor;
import io.quarkus.deployment.dev.filesystem.watch.FileChangeCallback;
import io.quarkus.deployment.dev.filesystem.watch.FileChangeEvent;
import io.quarkus.deployment.dev.filesystem.watch.WatchServiceFileSystemWatcher;

public class DevWatcherProcessor {

    private static final Logger LOG = Logger.getLogger(DevWatcherProcessor.class);
    private static volatile DevWatcher watcher;

    @BuildStep(onlyIf = IsDevelopment.class)
    @Consume(LiveReloadBuildItem.class)
    DevWatcherHistoryBuildItem getHistory(WebBundlerConfig config) {
        if (!config.browserLiveReload() || watcher == null) {
            return null;
        }
        return new DevWatcherHistoryBuildItem(watcher.changesHistory());
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Produce(DevWatcherStartedBuildItem.class)
    void startWatch(WebBundlerConfig config,
            List<WebBundlerWatchedDirBuildItem> watchedDirItems,
            List<DevWatchedLinkBuildItem> linkedDirItems,
            List<WebDirBuildItem> webDirItems) {
        if (!config.browserLiveReload()) {
            return;
        }
        final List<Path> webDirs = webDirItems.stream().map(WebDirBuildItem::path).toList();
        final Map<Path, List<DevWatcher.Link>> linkedDirs = linkedDirItems.stream().collect(
                Collectors.groupingBy(DevWatchedLinkBuildItem::source,
                        Collectors.mapping(d -> new DevWatcher.Link(d.target(), d.symbolic()), Collectors.toList())));
        final Set<Path> watchedDirs = watchedDirItems.stream().map(WebBundlerWatchedDirBuildItem::path)
                .collect(Collectors.toSet());
        if (watcher == null) {
            watcher = new DevWatcher(webDirs, linkedDirs, watchedDirs);
        } else {
            watcher.reload(webDirs, linkedDirs, watchedDirs);
        }
    }

    public static boolean detectedAddOrRemoveChanges(Collection<FileChangeEvent> changes) {
        return changes.stream().anyMatch(c -> c.getType() != FileChangeEvent.Type.MODIFIED);
    }

    final static class DevWatcher {

        private static final SkippingExecutor BUILD_EXECUTOR = new SkippingExecutor();

        private static final Logger LOG = Logger.getLogger(DevWatcher.class);
        private final WatchServiceFileSystemWatcher watcher;

        private volatile boolean started = false;
        private final AtomicReference<List<Path>> webDirs;
        private Set<Path> watchedDirs = new HashSet<>();
        private final AtomicReference<Map<Path, List<Link>>> watchedLinks;
        private final FileChangeCallback callback;
        private final AtomicReference<List<FileChangeEvent>> changesHistory;

        public DevWatcher(List<Path> webDirs, Map<Path, List<Link>> links, Set<Path> watchedDirs) {
            this.webDirs = new AtomicReference<>(webDirs);
            this.watchedLinks = new AtomicReference<>(links);
            this.changesHistory = new AtomicReference<>(new ArrayList<>());
            this.watchedDirs = watchedDirs;
            this.callback = this::handleChanges;
            this.watcher = new WatchServiceFileSystemWatcher("Dev Watcher", true);
            this.watchedDirs.forEach(dir -> watcher.watchDirectoryRecursively(dir, callback));
            this.started = true;
        }

        public void close() {
            try {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Stopping Dev Watcher");
                }
                started = false;
                watcher.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public List<FileChangeEvent> changesHistory() {
            return changesHistory.get();
        }

        public void reload(List<Path> webDirs, Map<Path, List<Link>> links, Set<Path> watchedDirs) {
            changesHistory.getAndSet(new ArrayList<>());
            this.watchedLinks.set(links);
            this.webDirs.set(webDirs);
            Set<Path> previousDirs = this.watchedDirs;
            this.watchedDirs = watchedDirs;
            reconcileWatchedDirs(previousDirs, watchedDirs);
            started = true;
        }

        public void reconcileWatchedDirs(Collection<Path> previousDirs, Collection<Path> watchedDirs) {
            for (Path previousDir : previousDirs) {
                watcher.unwatchPath(previousDir, callback);
            }
            for (Path watchedDir : watchedDirs) {
                watcher.watchDirectoryRecursively(watchedDir, callback);
            }
        }

        private void handleChanges(Collection<FileChangeEvent> changes) {
            if (!started || changes.stream()
                    .allMatch(c -> Files.isDirectory(c.getFile()) && c.getType() == FileChangeEvent.Type.MODIFIED))
                return;

            this.changesHistory.get().addAll(changes);

            boolean web = isWebChange(changes.stream().map(FileChangeEvent::getFile).toList());

            LOG.debugf("%s change detected on filesystem (%d): %s", web ? "Web file" : "File", changes.size(),
                    changes.stream().map(c -> c.getType() + " " + c.getFile()).toList());

            if (web) {
                if (detectedAddOrRemoveChanges(changes)) {
                    // This makes sure we build even if there was an error in a previous build
                    RuntimeUpdatesProcessor.INSTANCE.setRemoteProblem(null);
                    doScan(true);
                } else if (changes.stream().anyMatch(c -> watchedLinks.get().containsKey(c.getFile()))) {
                    runWebBuild();
                } else {
                    doScan(false);
                }
            } else {
                doScan(false);
            }
        }

        private boolean isWebChange(Collection<Path> changes) {
            return changes.stream().anyMatch(c -> webDirs.get().stream().anyMatch(c::startsWith));
        }

        private void runWebBuild() {
            LOG.info("Changes detected in web file(s), triggering a new Web Bundling...");
            copyWebLinksIfNeed();
            BUILD_EXECUTOR.execute(DevBundleProcessor::build);
        }

        private void copyWebLinksIfNeed() {
            for (Map.Entry<Path, List<Link>> entry : watchedLinks.get().entrySet()) {
                for (Link link : entry.getValue()) {
                    if (!link.symbolic()) {
                        try {
                            Files.copy(entry.getKey(), link.target(), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        private void doScan(boolean forceRestart) {
            BUILD_EXECUTOR.executeIfNotRunning(() -> {
                LOG.infof("Checking for Quarkus build (forceRestart: %s)", forceRestart);
                RuntimeUpdatesProcessor.INSTANCE.doScan(false, forceRestart);
            });
        }

        public record Link(Path target, boolean symbolic) {
        }

        public static class SkippingExecutor {
            private final ExecutorService executor;
            private final AtomicBoolean running = new AtomicBoolean(false);

            public SkippingExecutor() {
                this.executor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "Dev Watcher - Build Executor");
                    t.setDaemon(true);
                    return t;
                });
            }

            public void execute(Runnable task) {
                executor.execute(task);
            }

            public void executeIfNotRunning(Runnable task) {
                if (running.compareAndSet(false, true)) {
                    executor.execute(() -> {
                        try {
                            task.run();
                        } finally {
                            running.set(false);
                        }
                    });
                }
            }
        }
    }

}
