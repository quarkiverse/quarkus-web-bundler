package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import io.quarkus.deployment.dev.RuntimeUpdatesProcessor;
import io.quarkus.deployment.dev.filesystem.watch.FileChangeEvent;
import io.quarkus.deployment.dev.filesystem.watch.WatchServiceFileSystemWatcher;

public final class Watcher {
    private static final Logger LOG = Logger.getLogger(Watcher.class);
    private static final AtomicReference<WatchServiceFileSystemWatcher> watcherRef = new AtomicReference<>();
    private final static AtomicReference<ExecutorService> reloadExecutorRef = new AtomicReference<>();

    public static void start() {
        watcherRef.updateAndGet(r -> {
            if (r == null) {
                LOG.info("start");
                return new WatchServiceFileSystemWatcher("Web Bundler - Dev Watcher", true);
            }
            return r;
        });
    }

    public static void start(boolean force) {
        if (!force) {
            start();
            return;
        }
        watcherRef.updateAndGet(r -> {
            if (r != null) {
                try {
                    LOG.info("restart");
                    r.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                LOG.info("start");
            }
            return new WatchServiceFileSystemWatcher("Web Bundler - Dev Watcher", true);
        });
    }

    public static void watchDirectoryRecursively(Path directory, boolean web) {
        if (LOG.isDebugEnabled()) {
            LOG.debugf("watch directory recursively: %s", directory);
        }

        get().watchDirectoryRecursively(directory, web ? Watcher::handleWebChanges : Watcher::handleOtherChanges);
    }

    public static void watchFiles(Path directory, List<Path> monitoredFiles, boolean web) {
        if (LOG.isDebugEnabled()) {
            LOG.debugf("watch files: %s", monitoredFiles);
        }
        get().watchFiles(directory, monitoredFiles, web ? Watcher::handleWebChanges : Watcher::handleOtherChanges);
    }

    public static void stop() {
        watcherRef.updateAndGet(r -> {
            if (r == null) {
                return null;
            }
            try {
                LOG.info("stop");
                r.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        });
    }

    private static WatchServiceFileSystemWatcher get() {
        final WatchServiceFileSystemWatcher w = watcherRef.get();
        if (w == null) {
            throw new IllegalStateException("Web Bundler - Dev Watcher not started");
        }
        return w;
    }

    private static ExecutorService getReloadExecutor() {
        return reloadExecutorRef.updateAndGet(executor -> {
            if (executor == null || executor.isShutdown() || executor.isTerminated()) {
                return Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "Web Bundler - Dev Watcher - Restart");
                    t.setDaemon(true);
                    return t;
                });
            }
            return executor;
        });
    }

    public static void handleWebChanges(Collection<FileChangeEvent> changes) {
        if (changes.stream().allMatch(c -> Files.isDirectory(c.getFile()))) {
            return;
        }
        LOG.infof("Events received (%d): %s", changes.size(), changes.stream()
                .map(c -> c.getType() + " " + c.getFile())
                .toList());
        if (changes.stream().anyMatch(c -> c.getType() != FileChangeEvent.Type.MODIFIED)) {
            doScan(true);
        } else {
            doScan(false);
        }

    }

    private static void doScan(boolean forceRestart) {
        getReloadExecutor().execute(() -> RuntimeUpdatesProcessor.INSTANCE.doScan(false, forceRestart));
    }

    public static void handleOtherChanges(Collection<FileChangeEvent> changes) {
        if (changes.stream().allMatch(c -> Files.isDirectory(c.getFile()))) {
            return;
        }
        LOG.infof("Events received (%d): %s", changes.size(), changes.stream()
                .map(c -> c.getType() + " " + c.getFile())
                .toList());
        doScan(false);
    }
}
