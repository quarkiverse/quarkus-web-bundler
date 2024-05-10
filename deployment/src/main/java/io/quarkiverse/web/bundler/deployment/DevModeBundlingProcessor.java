package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.BundlingProcessor.handleBundleDistDir;
import static io.quarkiverse.web.bundler.deployment.BundlingProcessor.processGeneratedEntryPoints;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.Bundler;
import io.mvnpm.esbuild.Watch;
import io.mvnpm.esbuild.model.BundleOptions;
import io.quarkiverse.web.bundler.deployment.items.GeneratedBundleBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedEntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ReadyForBundlingBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;

public class DevModeBundlingProcessor {

    private static final Logger LOGGER = Logger.getLogger(DevModeBundlingProcessor.class);

    private static final String DEV_SERVICE_NAME = "web-bundler-dev";
    private static volatile DevServicesResultBuildItem.RunningDevService devService;
    private static final AtomicReference<CountDownLatch> WAITER = new AtomicReference<>();
    private static volatile long lastBundling = 0;

    @BuildStep(onlyIf = IsDevelopment.class)
    void watch(WebBundlerConfig config,
            ReadyForBundlingBuildItem readyForBundling,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            BuildProducer<DevServicesResultBuildItem> devServices,
            BuildProducer<GeneratedEntryPointBuildItem> generatedEntryPointProducer,
            LiveReloadBuildItem liveReload,
            CuratedApplicationShutdownBuildItem shutdown) {
        final BundlesBuildContext bundlesBuildContext = liveReload.getContextObject(BundlesBuildContext.class);
        final boolean isLiveReload = liveReload.isLiveReload()
                && bundlesBuildContext != null
                && bundlesBuildContext.bundleDistDir() != null
                && Files.isDirectory(bundlesBuildContext.bundleDistDir());
        if (isLiveReload && devService != null) {
            boolean shouldShutdownTheBroker = !WebBundlerConfig.BundlingConfig.isEqual(config.bundling(),
                    bundlesBuildContext.bundlingConfig());
            if (!shouldShutdownTheBroker) {
                // We should just wait for the change to happen
                devServices.produce(devService.toBuildItem());
                waitForBundling(readyForBundling, config, bundlesBuildContext, staticResourceProducer, generatedBundleProducer,
                        generatedEntryPointProducer);
                return;
            }
            shutdownDevService();
        }

        if (!isLiveReload) {
            Runnable closeTask = () -> {
                if (devService != null) {
                    shutdownDevService();
                }
                devService = null;
            };
            shutdown.addCloseTask(closeTask, true);
        }

        try {
            final Watch watch = Bundler.watch(readyForBundling.bundleOptions(), () -> {
                lastBundling = Instant.now().toEpochMilli();
                final CountDownLatch countDownLatch = WAITER.get();
                if (countDownLatch != null) {
                    countDownLatch.countDown();
                    LOGGER.debug("Watcher bundling event received");
                }
            }, false);
            devService = new DevServicesResultBuildItem.RunningDevService(
                    DEV_SERVICE_NAME, null, watch::stop, new HashMap<>());
            devServices.produce(devService.toBuildItem());
            final BundlesBuildContext newBundlesBuildContext = new BundlesBuildContext(readyForBundling.bundleOptions(),
                    config.bundling(), watch.dist());
            waitForBundling(readyForBundling, config, newBundlesBuildContext, staticResourceProducer, generatedBundleProducer,
                    generatedEntryPointProducer);
            liveReload.setContextObject(BundlesBuildContext.class, newBundlesBuildContext);
        } catch (IOException e) {
            liveReload.setContextObject(BundlesBuildContext.class, new BundlesBuildContext());
            throw new UncheckedIOException(e);
        }
    }

    private void shutdownDevService() {
        LOGGER.debug("Web Bundler: shutdownDevService");
        if (devService != null) {
            try {
                devService.close();
            } catch (Throwable e) {
                LOGGER.error("Failed to stop Web Bundler bundling process", e);
            } finally {
                devService = null;
            }
        }
    }

    private void waitForBundling(ReadyForBundlingBuildItem readyForBundling, WebBundlerConfig config,
            BundlesBuildContext bundlesBuildContext, BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            BuildProducer<GeneratedEntryPointBuildItem> generatedEntryPointProducer) {
        final long buildStart = Instant.now().toEpochMilli();
        if (readyForBundling.started() != null) {
            if (lastBundling > readyForBundling.started()) {
                LOGGER.debug("Bundling done, no need to wait");
            } else {
                final CountDownLatch latch = new CountDownLatch(1);
                final CountDownLatch existingLatch = WAITER.getAndSet(latch);
                WAITER.set(latch);
                LOGGER.debug("Waiting for bundling");
                try {
                    latch.await(2, TimeUnit.SECONDS);
                    LOGGER.debug("Bundling completed!");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    shutdownDevService();
                    throw new RuntimeException(e);
                } finally {
                    if (existingLatch != null) {
                        // this will always countdown the previous latch,
                        // it has no effect if it was already done
                        existingLatch.countDown();
                    }
                }
            }
        }
        handleBundleDistDir(config, generatedBundleProducer, staticResourceProducer, bundlesBuildContext.bundleDistDir(),
                buildStart);
        processGeneratedEntryPoints(config, bundlesBuildContext.bundleOptions().workDir(), generatedEntryPointProducer);
    }

    record BundlesBuildContext(BundleOptions bundleOptions,
            WebBundlerConfig.BundlingConfig bundlingConfig,
            Path bundleDistDir) {

        public BundlesBuildContext() {
            this(null, null, null);
        }
    }
}
