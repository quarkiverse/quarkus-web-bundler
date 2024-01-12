package io.quarkiverse.web.bundler.deployment.staticresources;

import static io.quarkiverse.web.bundler.deployment.staticresources.GeneratedStaticResourceBuildItem.WatchMode.DISABLED;
import static io.quarkiverse.web.bundler.deployment.staticresources.GeneratedStaticResourceBuildItem.WatchMode.RESTART;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;

public class GeneratedStaticResourcesProcessor {
    private static final Logger LOGGER = Logger.getLogger(GeneratedStaticResourcesProcessor.class);

    @BuildStep
    public void processStaticFiles(
            List<GeneratedStaticResourceBuildItem> staticResources,
            BuildProducer<GeneratedResourceBuildItem> prodResourcesProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourcesProducer,
            BuildProducer<AdditionalStaticResourceBuildItem> vertxStaticResourcesProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            CurateOutcomeBuildItem curateOutcome,
            OutputTargetBuildItem outputTarget,
            LiveReloadBuildItem liveReload,
            LaunchModeBuildItem launchModeBuildItem) {
        if (staticResources.isEmpty()) {
            return;
        }
        final Path buildDir = launchModeBuildItem.getLaunchMode().isDevOrTest() ? getBuildDirectory(outputTarget, curateOutcome)
                : null;
        final StaticResourcesDevContext staticResourcesDevContext = liveReload
                .getContextObject(StaticResourcesDevContext.class);
        if (liveReload.isLiveReload() && staticResourcesDevContext != null) {
            staticResourcesDevContext.getGeneratedStaticResourceNames().forEach(r -> {
                // Check that this resource is still generated (delete if not)
                // In some cases (like deleting a file without restarting),
                // a build tool clean might be necessary to make sure the static resources are clean
                if (staticResources.stream().map(GeneratedStaticResourceBuildItem::getResourceName).noneMatch(r::equals)) {
                    try {
                        Files.deleteIfExists(buildDir.resolve(r));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        final Set<String> generatedStaticFiles = new HashSet<>();
        for (GeneratedStaticResourceBuildItem staticResource : staticResources) {
            // watch origins files
            if (!staticResource.getOrigins().isEmpty() && !DISABLED.equals(staticResource.getWatchMode())) {
                for (GeneratedStaticResourceBuildItem.Source origin : staticResource.getOrigins()) {
                    watchedFiles.produce(
                            new HotDeploymentWatchedFileBuildItem(origin.getResourceName(),
                                    RESTART.equals(staticResource.getWatchMode())));
                }
            }
            // generated resource for prod
            prodResourcesProducer.produce(new GeneratedResourceBuildItem(staticResource.getResourceName(),
                    staticResource.getContent(), true));
            // for native
            if (staticResource.isNativeEnabled()) {
                nativeImageResourcesProducer.produce(new NativeImageResourceBuildItem(staticResource.getResourceName()));
            }

            // for vertx http
            vertxStaticResourcesProducer
                    .produce(new AdditionalStaticResourceBuildItem(prefixWithSlash(staticResource.getPublicPath()), false));
            // for dev/test mode
            if (launchModeBuildItem.getLaunchMode().isDevOrTest()) {
                Path targetPath = buildDir.resolve(staticResource.getResourceName());
                // TODO: Change detection could also be done automatically by comparing the content (might be slow) or a hash of it using dev context
                if (!Files.exists(targetPath) || staticResource.isChanged()) {
                    try {
                        Files.deleteIfExists(targetPath);
                        Files.createDirectories(targetPath.getParent());
                        Files.write(targetPath, staticResource.getContent());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            generatedStaticFiles.add(staticResource.getResourceName());
        }
        liveReload.setContextObject(StaticResourcesDevContext.class, new StaticResourcesDevContext(generatedStaticFiles));
    }

    public static Path getBuildDirectory(OutputTargetBuildItem outputTarget, CurateOutcomeBuildItem curateOutcomeBuildItem) {
        if (Files.exists(outputTarget.getOutputDirectory().resolve("classes/META-INF/resources"))) {
            return outputTarget.getOutputDirectory().resolve("classes");
        }
        if (Files.exists(outputTarget.getOutputDirectory().resolve("resources/main/META-INF/resources"))) {
            return outputTarget.getOutputDirectory().resolve("resources/main");
        }
        Path buildDir = null;
        ArtifactSources src = curateOutcomeBuildItem.getApplicationModel().getAppArtifact().getSources();
        if (src != null) { // shouldn't be null in dev mode
            Collection<SourceDir> dirs = src.getResourceDirs();
            if (dirs.isEmpty()) {
                // in the module has no resources dir?
                dirs = src.getSourceDirs();
            }
            if (!dirs.isEmpty()) {
                final Set<Path> outputDirs = dirs.stream().map(SourceDir::getOutputDir).collect(Collectors.toSet());
                // pick the first resources output dir
                buildDir = outputDirs.iterator().next();
                if (outputDirs.size() > 1) {
                    LOGGER.warnf("Multiple resources directories found, using the first one in the list: %s",
                            outputDirs);
                }
            }
        }

        return buildDir;
    }
}
