package io.quarkiverse.web.bundler.deployment.web;

import static io.quarkiverse.web.bundler.runtime.WebBundlerResourceHandler.DEFAULT_ROUTE_ORDER;
import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.ReadyForBundlingBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkiverse.web.bundler.deployment.util.PathUtils;
import io.quarkiverse.web.bundler.runtime.WebBundlerResourceRecorder;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;

public class GeneratedWebResourcesProcessor {
    private static final Logger LOGGER = Logger.getLogger(GeneratedWebResourcesProcessor.class);
    public static final String WEB_BUNDLER_LIVE_RELOAD_PATH = "/web-bundler/live";

    @BuildStep
    public void processStaticFiles(
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> notFoundPageProducer,
            List<GeneratedWebResourceBuildItem> staticResources,
            WebBundlerTargetDirBuildItem targetDir,
            BuildProducer<GeneratedResourceBuildItem> prodResourcesProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourcesProducer,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchModeBuildItem) {
        if (staticResources.isEmpty()) {
            return;
        }
        if (launchModeBuildItem.getLaunchMode().isDevOrTest()) {
            final Path distDir = targetDir.dist();
            final GeneratedWebResourcesDevContext generatedWebResourcesDevContext = liveReload
                    .getContextObject(GeneratedWebResourcesDevContext.class);
            Map<String, String> hashes = new HashMap<>();
            if (liveReload.isLiveReload() && generatedWebResourcesDevContext != null) {
                final Map<String, String> cachedHashes = new HashMap<>(
                        generatedWebResourcesDevContext.generatedResourcesHashes());
                for (GeneratedWebResourceBuildItem r : staticResources) {
                    String publicPath = r.publicPath();
                    final String cached = cachedHashes.get(publicPath);
                    if (cached == null) {
                        createGeneratedResourceOnDisk(r, distDir);
                    } else if (!cached.equals(r.contentHash())) {
                        createGeneratedResourceOnDisk(r, distDir);
                    }
                    cachedHashes.remove(r.publicPath());
                    hashes.put(r.publicPath(), r.contentHash());
                }

            } else {
                // Write the files
                for (GeneratedWebResourceBuildItem r : staticResources) {
                    hashes.put(r.publicPath(), r.contentHash());
                    createGeneratedResourceOnDisk(r, distDir);
                }
            }

            liveReload.setContextObject(GeneratedWebResourcesDevContext.class, new GeneratedWebResourcesDevContext(hashes));
        }

        for (GeneratedWebResourceBuildItem staticResource : staticResources) {
            notFoundPageProducer.produce(new NotFoundPageDisplayableEndpointBuildItem(staticResource.publicPath()));
            // generated resource for prod
            prodResourcesProducer.produce(new GeneratedResourceBuildItem(staticResource.resourceName(),
                    staticResource.content(), false));
            // for native
            nativeImageResourcesProducer.produce(new NativeImageResourceBuildItem(staticResource.resourceName()));
        }
    }

    private static void createGeneratedResourceOnDisk(GeneratedWebResourceBuildItem r, Path buildDir) {
        final Path targetPath = buildDir.resolve(PathUtils.removeLeadingSlash(r.publicPath()));
        try {
            Files.deleteIfExists(targetPath);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, r.content());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BuildStep
    @Record(RUNTIME_INIT)
    public void runtimeInit(
            LaunchModeBuildItem launchMode,
            WebBundlerTargetDirBuildItem targetDir,
            List<GeneratedWebResourceBuildItem> staticResources,
            WebBundlerResourceRecorder recorder,
            BuildProducer<RouteBuildItem> routes) throws IOException {
        if (!staticResources.isEmpty()) {
            String webDir = launchMode.getLaunchMode().isDevOrTest()
                    ? targetDir.dist().toAbsolutePath().toString()
                    : null;

            routes.produce(RouteBuildItem.builder().orderedRoute("/*", DEFAULT_ROUTE_ORDER)
                    .handler(recorder.createHandler(webDir,
                            staticResources.stream().map(GeneratedWebResourceBuildItem::publicPath)
                                    .collect(Collectors.toSet()),
                            launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT))
                    .build());

        }
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(RUNTIME_INIT)
    public void initChangeEventHandler(
            WebBundlerConfig config,
            WebBundlerTargetDirBuildItem targetDir,
            WebBundlerResourceRecorder recorder,
            ReadyForBundlingBuildItem readyForBundling,
            List<GeneratedWebResourceBuildItem> staticResources,
            ShutdownContextBuildItem shutdownContext,
            BuildProducer<RouteBuildItem> routes) {
        if (config.browserLiveReload() && readyForBundling != null) {
            routes.produce(RouteBuildItem.builder().route(WEB_BUNDLER_LIVE_RELOAD_PATH)
                    .handler(recorder.createChangeEventHandler(targetDir.dist().toAbsolutePath().toString(),
                            staticResources.stream().map(GeneratedWebResourceBuildItem::publicPath)
                                    .collect(Collectors.toSet()),
                            shutdownContext))
                    .build());
        }
    }

    public static String resolveFromRootPath(HttpBuildTimeConfig httpConfig, String path) {
        return PathUtils.join(httpConfig.rootPath, path);
    }
}
