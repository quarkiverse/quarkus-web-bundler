package io.quarkiverse.web.bundler.deployment.watcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

import io.quarkus.deployment.dev.RuntimeUpdatesProcessor;
import io.quarkus.deployment.dev.filesystem.watch.FileChangeEvent;
import io.quarkus.deployment.dev.filesystem.watch.WatchServiceFileSystemWatcher;

public final class DevWatcher {

    private static final SkippingExecutor BUILD_EXECUTOR = new SkippingExecutor();

    private static final Logger LOG = Logger.getLogger(DevWatcher.class);
    private final WatchServiceFileSystemWatcher watcher;

    private final List<Path> webDirs = new CopyOnWriteArrayList<>();
    private final Map<Path, Link> watchedLinks = new ConcurrentHashMap<>();
    private volatile Runnable runWebBuild;

    public DevWatcher() {
        this.watcher = new WatchServiceFileSystemWatcher("Dev Watcher", true);
    }

    public void close() {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping Dev Watcher");
            }
            watchedLinks.clear();
            webDirs.clear();
            watcher.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setWebDirs(List<Path> dirs) {
        webDirs.addAll(dirs);
    }

    public void addWatchedLink(Path source, Path target, boolean symbolic) {
        watchedLinks.put(source, new Link(target, symbolic));
    }

    public void watchDirectoryRecursively(Path directory) {
        if (LOG.isDebugEnabled()) {
            LOG.debugf("watch directory recursively: %s", directory);
        }
        watcher.watchDirectoryRecursively(directory, this::handleChanges);
    }

    public void watchFiles(Path directory, List<Path> monitoredFiles, boolean web) {
        if (LOG.isDebugEnabled()) {
            LOG.debugf("watch files: %s", monitoredFiles);
        }
        watcher.watchFiles(directory, monitoredFiles, this::handleChanges);
    }

    private void handleChanges(Collection<FileChangeEvent> changes) {
        if (changes.stream().allMatch(c -> Files.isDirectory(c.getFile())))
            return;

        boolean web = changes.stream().anyMatch(c -> webDirs.stream().anyMatch(w -> c.getFile().startsWith(w)));

        LOG.debugf("%s change detected on filesystem (%d): %s", web ? "Web file" : "File", changes.size(),
                changes.stream().map(c -> c.getType() + " " + c.getFile()).toList());

        if (web) {
            if (changes.stream().anyMatch(c -> c.getType() != FileChangeEvent.Type.MODIFIED)) {
                // This makes sure we build even if there was an error in a previous build
                RuntimeUpdatesProcessor.INSTANCE.setRemoteProblem(null);
                doScan(true);
            } else if (changes.stream().anyMatch(c -> watchedLinks.containsKey(c.getFile()))) {
                runWebBuild();
            } else {
                doScan(false);
            }
        } else {
            doScan(false);
        }
    }

    private void runWebBuild() {
        LOG.info("Triggering a new Web build...");
        copyWebLinksIfNeed();
        if (runWebBuild != null) {
            BUILD_EXECUTOR.execute(runWebBuild);
        }
    }

    private void copyWebLinksIfNeed() {
        for (Map.Entry<Path, Link> entry : watchedLinks.entrySet()) {
            if (!entry.getValue().symbolic()) {
                try {
                    Files.copy(entry.getKey(), entry.getValue().target(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
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

    public void setRunWebBuild(Runnable runWebBuild) {
        this.runWebBuild = runWebBuild;
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
