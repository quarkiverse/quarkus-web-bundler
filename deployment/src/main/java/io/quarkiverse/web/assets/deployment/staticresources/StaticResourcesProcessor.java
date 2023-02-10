package io.quarkiverse.web.assets.deployment.staticresources;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;

public class StaticResourcesProcessor {

    @BuildStep
    public void processSassFiles(
            List<StaticResourceBuildItem> staticResources,
            BuildProducer<GeneratedResourceBuildItem> prodResourcesProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourcesProducer,
            BuildProducer<AdditionalStaticResourceBuildItem> vertxStaticResourcesProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            CurateOutcomeBuildItem curateOutcome) {
        File buildDir = getBuildDirectory(curateOutcome);
        for (StaticResourceBuildItem staticResource : staticResources) {
            // watch origins files
            if (!staticResource.getOrigins().isEmpty() && staticResource.isWatchEnabled()) {
                for (StaticResourceBuildItem.Source origin : staticResource.getOrigins()) {
                    watchedFiles.produce(
                            new HotDeploymentWatchedFileBuildItem(origin.getResourceName(), false));
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
            if (!Files.exists(targetPath)) {
                try {
                    Files.createDirectories(targetPath.getParent());
                    Files.write(targetPath, staticResource.getContent());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

        }

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
