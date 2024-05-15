package io.quarkiverse.web.bundler.runtime.devmode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.runtime.WebBundlerResourceRecorder;
import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class WebBundlerHotReplacementSetup implements HotReplacementSetup {

    private static final Logger LOGGER = Logger.getLogger(WebBundlerHotReplacementSetup.class);

    private final List<Consumer<Set<String>>> changeEventListeners = new CopyOnWriteArrayList<>();
    static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    private volatile long nextUpdate = 0;
    private ScheduledFuture<?> scheduler;
    private HotReplacementContext context;

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        this.context = context;
        context.consumeNoRestartChanges(this::noRestartChanges);
        WebBundlerResourceRecorder.setHotDeploymentEventHandlerRegister((r) -> {
            changeEventListeners.add(r);
            return () -> changeEventListeners.remove(r);
        });
        WebBundlerResourceRecorder.setStartWatchScheduler(this::startWatchScheduler);
    }

    private void startWatchScheduler() {
        if (scheduler == null) {
            scheduler = EXECUTOR.scheduleAtFixedRate(() -> {
                try {
                    if (context.getDeploymentProblem() == null) {
                        // Let's not scan when there is a deployment problem
                        // and wait for another request to trigger the scan
                        if (System.currentTimeMillis() > nextUpdate) {
                            if (context.doScan(false)) {
                                LOGGER.debug("App restarted from watcher, let's wait 5s before watching again");
                                // If we have restarted, let's wait 5s before another scan
                                nextUpdate = System.currentTimeMillis() + 5000;
                            } else {
                                nextUpdate = System.currentTimeMillis() + 1000;
                            }
                        }
                    }

                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }, 500, 500, TimeUnit.MILLISECONDS);
        }

    }

    @Override
    public void close() {
        HotReplacementSetup.super.close();
        if (scheduler != null) {
            scheduler.cancel(false);
            scheduler = null;
        }
    }

    private void noRestartChanges(Set<String> strings) {
        for (Consumer<Set<String>> changeEventListener : changeEventListeners) {
            changeEventListener.accept(strings);
        }
    }
}
