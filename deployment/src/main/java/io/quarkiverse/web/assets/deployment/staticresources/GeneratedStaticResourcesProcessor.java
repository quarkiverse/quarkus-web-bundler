package io.quarkiverse.web.assets.deployment.staticresources;

import static io.quarkiverse.web.assets.deployment.staticresources.GeneratedStaticResourceBuildItem.WatchMode.DISABLED;
import static io.quarkiverse.web.assets.deployment.staticresources.GeneratedStaticResourceBuildItem.WatchMode.RESTART;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;

public class GeneratedStaticResourcesProcessor {

    @BuildStep
    public void processStaticFiles(
            List<GeneratedStaticResourceBuildItem> staticResources,
            BuildProducer<GeneratedResourceBuildItem> prodResourcesProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourcesProducer,
            BuildProducer<AdditionalStaticResourceBuildItem> vertxStaticResourcesProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            CurateOutcomeBuildItem curateOutcome,
            LiveReloadBuildItem liveReload) {
        final File buildDir = getBuildDirectory(curateOutcome);
        final StaticResourcesDevContext staticResourcesDevContext = liveReload
                .getContextObject(StaticResourcesDevContext.class);
        if (liveReload.isLiveReload() && staticResourcesDevContext != null) {
            staticResourcesDevContext.getGeneratedStaticResourceNames().forEach(r -> {
                // Check that this resource is still generated (delete if not)
                // In some cases (like deleting a file without restarting),
                // a build tool clean might be necessary to make sure the static resources are clean
                if (staticResources.stream().map(GeneratedStaticResourceBuildItem::getResourceName).noneMatch(r::equals)) {
                    try {
                        Files.deleteIfExists(buildDir.toPath().resolve(r));
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
            vertxStaticResourcesProducer.produce(new AdditionalStaticResourceBuildItem(staticResource.getPublicPath(), false));
            // for dev/test mode
            Path targetPath = buildDir.toPath().resolve(staticResource.getResourceName());
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
            generatedStaticFiles.add(staticResource.getResourceName());
        }
        liveReload.setContextObject(StaticResourcesDevContext.class, new StaticResourcesDevContext(generatedStaticFiles));
    }

    public static File getBuildDirectory(CurateOutcomeBuildItem curateOutcomeBuildItem) {
        File buildDir = null;
        ArtifactSources src = curateOutcomeBuildItem.getApplicationModel().getAppArtifact().getSources();
        if (src != null) { // shouldn't be null in dev mode
            Collection<SourceDir> srcDirs = src.getResourceDirs();
            if (srcDirs.isEmpty()) {
                // in the module has no resources dir?
                srcDirs = src.getSourceDirs();
            }
            if (!srcDirs.isEmpty()) {
                // pick the first resources output dir
                Path resourcesOutputDir = srcDirs.iterator().next().getOutputDir();
                buildDir = resourcesOutputDir.toFile();
            }
        }
        if (buildDir == null) {
            // the module doesn't have any sources nor resources, stick to the build dir
            buildDir = new File(
                    curateOutcomeBuildItem.getApplicationModel().getAppArtifact().getWorkspaceModule().getBuildDir(),
                    "classes");
        }

        return buildDir;
    }
}
