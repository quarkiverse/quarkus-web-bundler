package io.quarkiverse.web.assets.sass.deployment;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.larsgrefer.sass.embedded.SassCompilationFailedException;
import io.quarkiverse.web.assets.sass.devmode.SassDevModeRecorder;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.deployment.IsDevelopment;
// import io.bit3.jsass.CompilationException;
// import io.bit3.jsass.Compiler;
// import io.bit3.jsass.Options;
// import io.bit3.jsass.Output;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.vertx.http.deployment.spi.AdditionalStaticResourceBuildItem;
import io.quarkus.vertx.http.runtime.StaticResourcesRecorder;
import sass.embedded_protocol.EmbeddedSass.OutboundMessage.CompileResponse.CompileFailure;

public class SassProcessor {

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep(onlyIf = IsDevelopment.class)
    public void setupRecorder(SassDevModeRecorder sassRecorder, List<SassDependencyBuildItem> sassDependencies,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {
        File buildDir = getBuildDirectory(curateOutcomeBuildItem);
        sassRecorder.clearHotReloadContext();
        sassRecorder.setBuildDir(buildDir.toString());
        for (SassDependencyBuildItem sassDependency : sassDependencies) {
            sassRecorder.addDependency(sassDependency.source, sassDependency.affectedFile);
        }
    }

    @BuildStep
    public void processSassFiles(BuildProducer<SassDependencyBuildItem> sassDependencies,
            BuildProducer<GeneratedResourceBuildItem> resources,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResources,
            BuildProducer<AdditionalStaticResourceBuildItem> staticResources,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFiles,
            OutputTargetBuildItem outputTargetBuildItem,
            ApplicationArchivesBuildItem archives,
            CurateOutcomeBuildItem curateOutcomeBuildItem) {
        File buildDir = getBuildDirectory(curateOutcomeBuildItem);
        // collect all errors instead of fail at the first one
        List<SassCompilationFailedException> errors = new ArrayList<>();
        archives.getRootArchive().accept(tree -> {
            tree.walk(pv -> {
                String relativePath = pv.getRelativePath("/");
                // FIXME: also watch partials
                if (relativePath.startsWith("META-INF/resources/")
                        && SassDevModeRecorder.isCompiledSassFile(pv.getPath().getFileName().toString())) {
                    // watch source file
                    watchedFiles.produce(new HotDeploymentWatchedFileBuildItem(relativePath, false));
                    // compile
                    try {
                        String result = BuildTimeCompiler.convertScss(pv.getPath(), relativePath, pv.getRoot(),
                                (source, affectedFile) -> sassDependencies
                                        .produce(new SassDependencyBuildItem(source, affectedFile)));
                        // figure out where we put it
                        String generatedFile = relativePath.substring(0, relativePath.length() - 5) + ".css";
                        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                        // generated resource for prod
                        resources.produce(new GeneratedResourceBuildItem(generatedFile,
                                bytes, true));
                        // for native
                        nativeImageResources.produce(new NativeImageResourceBuildItem(generatedFile));
                        // for vertx http
                        String additionalPath = generatedFile.substring(StaticResourcesRecorder.META_INF_RESOURCES.length());
                        staticResources.produce(new AdditionalStaticResourceBuildItem(additionalPath, false));
                        // for dev/test mode
                        Path targetPath = buildDir.toPath().resolve(generatedFile);
                        try {
                            Files.createDirectories(targetPath.getParent());
                            Files.write(targetPath, bytes);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    } catch (RuntimeException x) {
                        if (x.getCause() instanceof SassCompilationFailedException) {
                            // collect error
                            errors.add((SassCompilationFailedException) x.getCause());
                        } else {
                            throw x;
                        }
                    }
                }
            });
        });
        if (errors.size() == 1) {
            throw new RuntimeException(errors.get(0));
        } else if (errors.size() > 1) {
            // aggregate inside fake composite exception
            SassCompilationFailedException x = new SassCompilationFailedException(CompileFailure.newBuilder().build());
            for (SassCompilationFailedException error : errors) {
                x.addSuppressed(error);
            }
            throw new RuntimeException(x);
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
