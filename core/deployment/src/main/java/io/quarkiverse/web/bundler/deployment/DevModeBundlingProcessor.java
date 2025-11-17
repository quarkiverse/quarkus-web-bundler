package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.BundlingProcessor.bundleAndProcess;
import static io.quarkiverse.web.bundler.deployment.BundlingProcessor.handleBundleDistDir;
import static io.quarkiverse.web.bundler.deployment.BundlingProcessor.processGeneratedEntryPoints;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.Bundler;
import io.mvnpm.esbuild.BundlingException;
import io.mvnpm.esbuild.model.BundleOptions;
import io.mvnpm.esbuild.model.DevResult;
import io.quarkiverse.web.bundler.deployment.items.DevWatcherBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedBundleBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedEntryPointBuildItem;
import io.quarkiverse.web.bundler.deployment.items.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ReadyForBundlingBuildItem;
import io.quarkiverse.web.bundler.runtime.devmode.WebBundlingException;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CuratedApplicationShutdownBuildItem;
import io.quarkus.deployment.builditem.DevServicesResultBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.dev.RuntimeUpdatesProcessor;

public class DevModeBundlingProcessor {

    private static final Logger LOGGER = Logger.getLogger(DevModeBundlingProcessor.class);

    private static final String DEV_SERVICE_NAME = "web-bundler-dev";
    private static volatile DevServicesResultBuildItem.RunningDevService devService;
    private static volatile DevResult dev;

    @BuildStep(onlyIf = IsDevelopment.class)
    void watch(WebBundlerConfig config,
            DevWatcherBuildItem watcher,
            ReadyForBundlingBuildItem readyForBundling,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            BuildProducer<GeneratedBundleBuildItem> generatedBundleProducer,
            BuildProducer<DevServicesResultBuildItem> devServices,
            BuildProducer<GeneratedEntryPointBuildItem> generatedEntryPointProducer,
            LiveReloadBuildItem liveReload,
            CuratedApplicationShutdownBuildItem shutdown) {
        if (readyForBundling == null) {
            return;
        }

        if (watcher == null) {
            // We use normal bundling when esbuild watch is disabled
            bundleAndProcess(config, readyForBundling, staticResourceProducer,
                    generatedBundleProducer,
                    generatedEntryPointProducer);
            return;
        }

        if (dev != null) {
            shutdownDevService();
        }

        resetRemoteProblem();
        if (!liveReload.isLiveReload()) {
            shutdown.addCloseTask(DevModeBundlingProcessor::shutdownDevService, true);
        }

        try {
            dev = Bundler.dev(readyForBundling.bundleOptions(), false);
            devService = new DevServicesResultBuildItem.RunningDevService(
                    DEV_SERVICE_NAME, null, dev, new HashMap<>());
            devServices.produce(devService.toBuildItem());
            resetRemoteProblem();
            dev.process().build();
            watcher.get().setRunWebBuild(DevModeBundlingProcessor::build);
            handleBundleDistDir(config, generatedBundleProducer, staticResourceProducer, dev.process().dist(),
                    readyForBundling.fixedNames(), readyForBundling.startTime());
            processGeneratedEntryPoints(readyForBundling.bundleOptions().workDir(), generatedEntryPointProducer);
        } catch (BundlingException e) {
            throw new WebBundlingException(e.getMessage(), e.logs().errors());
        } catch (IOException e) {
            shutdownDevService();
            throw new UncheckedIOException(e);
        } catch (Exception e) {
            shutdownDevService();
            throw e;
        }
    }

    public static void build() {
        if (dev != null && dev.process().isAlive()) {
            try {
                dev.process().build();
                resetRemoteProblem();
                RuntimeUpdatesProcessor.INSTANCE.doScan(false, false);
                LOGGER.info("Web build successful");
            } catch (BundlingException e) {
                LOGGER.error("Web build Error");
                if (RuntimeUpdatesProcessor.INSTANCE.getCompileProblem() == null) {
                    RuntimeUpdatesProcessor.INSTANCE
                            .setRemoteProblem(new WebBundlingException(e.getMessage(), e.logs().errors()));
                }
                callNoRestartChangesConsumers(false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            LOGGER.warn("EsBuild Bundler dev service is not alive");
            shutdownDevService();
            RuntimeUpdatesProcessor.INSTANCE.doScan(false, true);
        }
    }

    private static void callNoRestartChangesConsumers(boolean isSuccess) {
        RuntimeUpdatesProcessor.INSTANCE
                .notifyExtensions(Set.of(isSuccess ? "web-bundler/build-success" : "web-bundler/build-error"));
    }

    private static void resetRemoteProblem() {
        if (RuntimeUpdatesProcessor.INSTANCE.getCompileProblem() instanceof WebBundlingException) {
            RuntimeUpdatesProcessor.INSTANCE.setRemoteProblem(null);
        }
    }

    private static void shutdownDevService() {
        LOGGER.info("Web Bundler: stopping dev bundling");
        try {
            if (dev != null) {
                dev.close();
            }
            if (devService != null) {
                devService.close();
            }
        } catch (Throwable e) {
            LOGGER.error("Failed to stop Web Bundler Bundling process", e);
        } finally {
            devService = null;
            dev = null;
        }
    }

    record BundlesBuildContext(BundleOptions bundleOptions,
            Path bundleDistDir) {

        public BundlesBuildContext() {
            this(null, null);
        }
    }

}
