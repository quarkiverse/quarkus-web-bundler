package io.quarkiverse.web.bundler.deployment.web;

import static io.quarkiverse.web.bundler.runtime.WebBundlerResourceHandler.DEFAULT_ROUTE_ORDER;
import static io.quarkiverse.web.bundler.runtime.WebBundlerResourceHandler.META_INF_WEB;
import static io.quarkus.deployment.annotations.ExecutionTime.RUNTIME_INIT;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.util.PathUtils;
import io.quarkiverse.web.bundler.runtime.WebBundlerResourceRecorder;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.ShutdownContextBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;

public class GeneratedWebResourcesProcessor {
    private static final Logger LOGGER = Logger.getLogger(GeneratedWebResourcesProcessor.class);
    public static final String WEB_BUNDLER_LIVE_RELOAD_PATH = "/web-bundler/live";

    @BuildStep
    public void processStaticFiles(
            HttpBuildTimeConfig httpConfig,
            List<GeneratedWebResourceBuildItem> staticResources,
            BuildProducer<GeneratedResourceBuildItem> prodResourcesProducer,
            BuildProducer<ChangeEventBuildItem> changeEventProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourcesProducer,
            CurateOutcomeBuildItem curateOutcome,
            OutputTargetBuildItem outputTarget,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchModeBuildItem) {
        if (staticResources.isEmpty()) {
            return;
        }
        if (launchModeBuildItem.getLaunchMode().isDevOrTest()) {
            final Path buildDir = getBuildDirectory(outputTarget, curateOutcome);
            final GeneratedWebResourcesDevContext generatedWebResourcesDevContext = liveReload
                    .getContextObject(GeneratedWebResourcesDevContext.class);
            Map<String, String> hashes = new HashMap<>();
            ChangeEventBuildItem.ChangeEventBuildItemBuilder changeEventBuilder = ChangeEventBuildItem.builder();
            if (liveReload.isLiveReload() && generatedWebResourcesDevContext != null) {
                final Map<String, String> cachedHashes = new HashMap<>(
                        generatedWebResourcesDevContext.generatedResourcesHashes());
                for (GeneratedWebResourceBuildItem r : staticResources) {
                    String publicPath = r.publicPath();
                    final String cached = cachedHashes.get(publicPath);
                    if (cached == null) {
                        createGeneratedResourceOnDisk(r, buildDir);
                        changeEventBuilder.addAdded(resolveFromRootPath(httpConfig, publicPath));
                    } else if (!cached.equals(r.contentHash())) {
                        createGeneratedResourceOnDisk(r, buildDir);
                        changeEventBuilder.addUpdated(resolveFromRootPath(httpConfig, publicPath));
                    }
                    cachedHashes.remove(r.publicPath());
                    hashes.put(r.publicPath(), r.contentHash());
                }
                cachedHashes.keySet().stream()
                        .map(s -> resolveFromRootPath(httpConfig, s))
                        .forEach(changeEventBuilder::addRemoved);

            } else {
                // Clean up the directory
                try {
                    FileUtil.deleteDirectory(buildDir.resolve(META_INF_WEB));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Write the files
                for (GeneratedWebResourceBuildItem r : staticResources) {
                    hashes.put(r.publicPath(), r.contentHash());
                    createGeneratedResourceOnDisk(r, buildDir);
                }
            }
            changeEventProducer.produce(changeEventBuilder.build());
            liveReload.setContextObject(GeneratedWebResourcesDevContext.class, new GeneratedWebResourcesDevContext(hashes));
        }

        for (GeneratedWebResourceBuildItem staticResource : staticResources) {
            // generated resource for prod
            prodResourcesProducer.produce(new GeneratedResourceBuildItem(staticResource.resourceName(),
                    staticResource.content(), false));
            // for native
            nativeImageResourcesProducer.produce(new NativeImageResourceBuildItem(staticResource.resourceName()));
        }
    }

    private static void createGeneratedResourceOnDisk(GeneratedWebResourceBuildItem r, Path buildDir) {
        final Path targetPath = buildDir.resolve(r.resourceName());
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
            ChangeEventBuildItem changeEvent,
            List<GeneratedWebResourceBuildItem> staticResources,
            WebBundlerResourceRecorder recorder,
            CurateOutcomeBuildItem curateOutcome,
            OutputTargetBuildItem outputTarget,
            BuildProducer<RouteBuildItem> routes) throws IOException {
        if (!staticResources.isEmpty()) {
            String metaInfWeb = launchMode.getLaunchMode().isDevOrTest()
                    ? getBuildDirectory(outputTarget, curateOutcome).resolve(META_INF_WEB).toAbsolutePath().toString()
                    : null;

            routes.produce(RouteBuildItem.builder().orderedRoute("/*", DEFAULT_ROUTE_ORDER)
                    .handler(recorder.createHandler(metaInfWeb,
                            staticResources.stream().map(GeneratedWebResourceBuildItem::publicPath)
                                    .collect(Collectors.toSet()),
                            launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT))
                    .build());

        }
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    @Record(RUNTIME_INIT)
    public void runtimeInit(
            ChangeEventBuildItem changeEvent,
            WebBundlerResourceRecorder recorder,
            ShutdownContextBuildItem shutdownContext,
            BuildProducer<RouteBuildItem> routes) {
        routes.produce(RouteBuildItem.builder().route(WEB_BUNDLER_LIVE_RELOAD_PATH)
                .handler(recorder.createChangeEventHandler(changeEvent.added(), changeEvent.removed(), changeEvent.updated(),
                        shutdownContext))
                .build());
    }

    public static Path getBuildDirectory(OutputTargetBuildItem outputTarget, CurateOutcomeBuildItem curateOutcomeBuildItem) {
        if (Files.exists(outputTarget.getOutputDirectory().resolve("classes/META-INF/resources"))) {
            return outputTarget.getOutputDirectory().resolve("classes");
        }
        if (Files.exists(outputTarget.getOutputDirectory().resolve("resources/main/META-INF/resources"))) {
            return outputTarget.getOutputDirectory().resolve("resources/main");
        }
        ArtifactSources src = curateOutcomeBuildItem.getApplicationModel().getAppArtifact().getSources();
        if (src != null) { // shouldn't be null in dev mode
            Collection<SourceDir> dirs = src.getResourceDirs();
            if (dirs.isEmpty()) {
                // in the module has no resources dir?
                dirs = src.getSourceDirs();
            }
            if (!dirs.isEmpty()) {
                final Set<Path> outputDirs = dirs.stream().map(SourceDir::getOutputDir).collect(Collectors.toSet());
                if (outputDirs.size() > 1) {
                    LOGGER.warnf("Multiple resources directories found, using the first one in the list: %s",
                            outputDirs);
                }
                // pick the first resources output dir
                return outputDirs.iterator().next();

            }
        }
        throw new RuntimeException("Unable to determine the build directory");
    }

    public static String resolveFromRootPath(HttpBuildTimeConfig httpConfig, String path) {
        return PathUtils.join(httpConfig.rootPath, path);
    }
}
