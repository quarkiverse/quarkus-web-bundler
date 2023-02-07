package io.quarkiverse.web.assets.sass.devmode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.jboss.logging.Logger;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class SassDevModeHandler implements HotReplacementSetup {

    private static Logger log = Logger.getLogger(SassDevModeHandler.class);

    private BiFunction<String[], BiConsumer<String, String>, String> devModeSassCompiler;
    private ClassLoader cl;
    private List<Path> resourcesPaths;
    private Path classesDir;

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        resourcesPaths = context.getResourcesDir();
        classesDir = context.getClassesDir();
        context.consumeNoRestartChanges(this::noRestartChanges);
        // TCCL is the Augmentation Class Loader, which is the same CL as the build steps
        cl = Thread.currentThread().getContextClassLoader();
    }

    public void noRestartChanges(Set<String> changes) {
        Set<String> needRebuild = new HashSet<>();
        for (String change : changes) {
            List<String> affectedFiles = SassDevModeRecorder.reverseDependencies.get(change);
            if (affectedFiles != null && !affectedFiles.isEmpty()) {
                needRebuild.addAll(affectedFiles);
            } else {
                Path changePath = Path.of(change);
                if (SassDevModeRecorder.isCompiledSassFile(changePath.getFileName().toString())) {
                    // must be a new file, let's build it
                    needRebuild.add(change);
                }
            }
        }
        log.infof("SASS changes detected, will rebuild: %s", needRebuild);
        // clear dependencies of files we compile before we collect them anew
        for (String path : needRebuild) {
            SassDevModeRecorder.resetDependencies(path);
        }
        if (!needRebuild.isEmpty()) {
            if (devModeSassCompiler == null) {
                try {
                    // TCCL is the Runtime Class Loader, but we want the build step CL
                    devModeSassCompiler = (BiFunction<String[], BiConsumer<String, String>, String>) cl
                            .loadClass("io.quarkiverse.web.assets.sass.deployment.BuildTimeCompiler").getDeclaredConstructor()
                            .newInstance();
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                }
            }
            if (devModeSassCompiler != null) {
                List<String> deleted = new ArrayList<>();
                NEXT_SOURCE: for (String relativePath : needRebuild) {
                    String generatedFile = relativePath.substring(0, relativePath.length() - 5) + ".css";
                    for (Path resourcesPath : resourcesPaths) {
                        Path absolutePath = resourcesPath.resolve(relativePath);
                        if (Files.exists(absolutePath)) {
                            String result = devModeSassCompiler.apply(new String[] {
                                    absolutePath.toString(),
                                    relativePath,
                                    resourcesPath.toString()
                            }, SassDevModeRecorder::addHotReloadDependency);
                            writeResult(result, relativePath, generatedFile);
                            continue NEXT_SOURCE;
                        }
                    }
                    deleted.add(relativePath);
                    Path targetPath = classesDir.resolve(generatedFile);
                    if (Files.exists(targetPath)) {
                        deleteResourceFile(targetPath);
                    }
                    // also from build step dir
                    targetPath = SassDevModeRecorder.buildDir.resolve(generatedFile);
                    if (Files.exists(targetPath)) {
                        deleteResourceFile(targetPath);
                    }
                }
                if (!deleted.isEmpty()) {
                    log.infof("SASS files deleted: %s", deleted);
                }
            }
        }
    }

    public void deleteResourceFile(Path resourceFilePath) {
        long old = modTime(resourceFilePath.getParent());
        long timeout = System.currentTimeMillis() + 5000;
        //in general there is a potential race here
        //if you serve a file you will send the data to the client, then close the resource
        //this means that by the time the client request is run the file may not
        //have been closed yet, as the test sees the response as being complete after the last data is send
        //we wait up to 5s for this condition to be resolved
        for (;;) {
            try {
                Files.delete(resourceFilePath);
                break;
            } catch (IOException e) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    //ignore
                }
                if (System.currentTimeMillis() < timeout) {
                    continue;
                }
                throw new UncheckedIOException(e);
            }
        }
        // wait for last modified time of the parent to get updated
        sleepForFileChanges(resourceFilePath.getParent(), old);
    }

    private void sleepForFileChanges(Path path, long oldTime) {
        try {
            //we avoid modifying the file twice
            //this can cause intermittent failures in the continuous testing tests
            long fm = modTime(path);
            if (fm > oldTime) {
                return;
            }
            //we want to make sure the last modified time is larger than both the current time
            //and the current last modified time. Some file systems only resolve file
            //time to the nearest second, so this is necessary for dev mode to pick up the changes
            long timeToBeat = Math.max(System.currentTimeMillis(), modTime(path));
            for (;;) {
                Thread.sleep(1000);
                Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()));
                fm = modTime(path);
                Thread.sleep(100);
                if (fm > timeToBeat) {
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long modTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeResult(String result, String relativePath, String generatedFile) {
        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);

        Path targetPath = classesDir.resolve(generatedFile);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
