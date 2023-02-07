package io.quarkiverse.web.assets.sass.devmode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class SassDevModeHandler implements HotReplacementSetup {

    public Function<String[], String> devModeSassCompiler;

    public static void clear() {
        SassDevModeRecorder.reverseDependencies.clear();
    }

    private ClassLoader cl;
    private List<Path> resourcesPaths;
    private Path classesDir;

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        System.err.println("Setting up SASS hot deploy classes: " + context.getClassesDir() + " resources dirs: "
                + context.getResourcesDir());
        resourcesPaths = context.getResourcesDir();
        classesDir = context.getClassesDir();
        context.consumeNoRestartChanges(this::noRestartChanges);
        // TCCL is the Augmentation Class Loader, which is the same CL as the build steps
        cl = Thread.currentThread().getContextClassLoader();
    }

    public void noRestartChanges(Set<String> changes) {
        System.err.println("No restart changes: " + changes + " dependencies: " + SassDevModeRecorder.reverseDependencies);
        Set<String> needRebuild = new HashSet<>();
        for (String change : changes) {
            List<String> affectedFiles = SassDevModeRecorder.reverseDependencies.get(change);
            if (affectedFiles != null) {
                needRebuild.addAll(affectedFiles);
            } else if (change.toLowerCase().endsWith(".scss")) {
                // must be a new file, let's build it
                System.err.println("New file: " + change);
                needRebuild.add(change);
            }
        }
        System.err.println("Need to rebuild: " + needRebuild + " with: " + devModeSassCompiler);
        if (!needRebuild.isEmpty()) {
            if (devModeSassCompiler == null) {
                try {
                    // TCCL is the Runtime Class Loader, but we want the build step CL
                    devModeSassCompiler = (Function<String[], String>) cl
                            .loadClass("io.quarkiverse.web.assets.sass.deployment.BuildTimeCompiler").getDeclaredConstructor()
                            .newInstance();
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                    e.printStackTrace();
                }
            }
            if (devModeSassCompiler != null) {
                NEXT_SOURCE: for (String relativePath : needRebuild) {
                    String generatedFile = relativePath.substring(0, relativePath.length() - 5) + ".css";
                    System.err.println("Result in " + generatedFile);
                    for (Path resourcesPath : resourcesPaths) {
                        Path absolutePath = resourcesPath.resolve(relativePath);
                        if (Files.exists(absolutePath)) {
                            // FIXME: collect new dependencies
                            String result = devModeSassCompiler.apply(new String[] {
                                    absolutePath.toString(),
                                    relativePath,
                                    resourcesPath.toString()
                            });
                            writeResult(result, relativePath, generatedFile);
                            continue NEXT_SOURCE;
                        }
                    }
                    System.err.println("Could not find source file for " + relativePath + " probably a deletion");
                    Path targetPath = classesDir.resolve(generatedFile);
                    if (Files.exists(targetPath)) {
                        System.err.println("Deleting from " + targetPath);
                        deleteResourceFile(targetPath);
                    }
                    // also from build step dir
                    targetPath = SassDevModeRecorder.buildDir.resolve(generatedFile);
                    if (Files.exists(targetPath)) {
                        System.err.println("Deleting from " + targetPath);
                        deleteResourceFile(targetPath);
                    }
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
            System.err.println("Wrote to " + targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
