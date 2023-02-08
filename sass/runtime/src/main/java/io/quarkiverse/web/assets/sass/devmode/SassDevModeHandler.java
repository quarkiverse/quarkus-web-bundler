package io.quarkiverse.web.assets.sass.devmode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.quarkus.dev.ErrorPageGenerators;
import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class SassDevModeHandler implements HotReplacementSetup {

    private static Logger log = Logger.getLogger(SassDevModeHandler.class);

    private BiFunction<String[], BiConsumer<String, String>, String> devModeSassCompiler;
    private Function<Throwable, String> devModeSassErrorPage;
    private ClassLoader cl;

    private HotReplacementContext context;

    private static final String SASS_EXCEPTION = "de.larsgrefer.sass.embedded.SassCompilationFailedException";
    private static final String SASS_COMPILE_FAILURE = "sass.embedded_protocol.EmbeddedSass.OutboundMessage.CompileResponse.CompileFailure";
    private static final String SASS_COMPILE_FAILURE_BUILDER = SASS_COMPILE_FAILURE + ".Builder";

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        this.context = context;
        context.consumeNoRestartChanges(this::noRestartChanges);
        // FIXME: due to remoteProblem not being cleared on reload
        // see https://github.com/quarkusio/quarkus/issues/31013
        context.addPreScanStep(this::preScan);
        // TCCL is the Augmentation Class Loader, which is the same CL as the build steps
        cl = Thread.currentThread().getContextClassLoader();
        ErrorPageGenerators.register(SASS_EXCEPTION, this::generatePage);
    }

    String generatePage(Throwable exception) {
        if (devModeSassErrorPage == null) {
            try {
                // TCCL is the Runtime Class Loader, but we want the build step CL
                devModeSassErrorPage = (Function<Throwable, String>) cl
                        .loadClass("io.quarkiverse.web.assets.sass.deployment.BuildTimeErrorPage").getDeclaredConstructor()
                        .newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                e.printStackTrace();
            }
        }
        if (devModeSassErrorPage != null) {
            return devModeSassErrorPage.apply(exception);
        }
        return "Failed to generate error page via reflection";
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
                List<Throwable> errors = new ArrayList<>();
                List<String> deleted = new ArrayList<>();
                NEXT_SOURCE: for (String relativePath : needRebuild) {
                    String generatedFile = relativePath.substring(0, relativePath.length() - 5) + ".css";
                    for (Path resourcesPath : context.getResourcesDir()) {
                        Path absolutePath = resourcesPath.resolve(relativePath);
                        if (Files.exists(absolutePath)) {
                            try {
                                String result = devModeSassCompiler.apply(new String[] {
                                        absolutePath.toString(),
                                        relativePath,
                                        resourcesPath.toString()
                                }, SassDevModeRecorder::addHotReloadDependency);
                                writeResult(result, relativePath, generatedFile);
                            } catch (RuntimeException x) {
                                if (x.getCause() != null
                                        && x.getCause().getClass().getName().equals(SASS_EXCEPTION)) {
                                    // collect error
                                    errors.add(x.getCause());
                                } else {
                                    throw x;
                                }
                            }
                            continue NEXT_SOURCE;
                        }
                    }
                    deleted.add(relativePath);
                    Path targetPath = context.getClassesDir().resolve(generatedFile);
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
                if (errors.size() == 1) {
                    context.setRemoteProblem(errors.get(0));
                } else if (errors.size() > 1) {
                    // aggregate inside fake composite exception
                    context.setRemoteProblem(makeAggregateSassException(errors));
                } else {
                    resetRemoteProblem();
                }
            }
        }
    }

    private Throwable makeAggregateSassException(List<Throwable> errors) {
        ClassLoader cl = errors.get(0).getClass().getClassLoader();
        try {
            Class<?> compileFailureClass = cl.loadClass(SASS_COMPILE_FAILURE);
            Method newBuilderMethod = compileFailureClass.getDeclaredMethod("newBuilder");
            Object newBuilder = newBuilderMethod.invoke(null);
            Class<?> compileFailureBuilderClass = cl.loadClass(SASS_COMPILE_FAILURE_BUILDER);
            Method buildMethod = compileFailureBuilderClass.getDeclaredMethod("build");
            Object compileFailure = buildMethod.invoke(newBuilder);
            Class<?> exceptionClass = cl.loadClass(SASS_EXCEPTION);
            Constructor<?> newExceptionConstructor = exceptionClass.getDeclaredConstructor(compileFailureClass);
            Exception x = (Exception) newExceptionConstructor.newInstance(compileFailure);
            for (Throwable error : errors) {
                x.addSuppressed(error);
            }
            return x;
        } catch (Exception x) {
            return x;
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

        Path targetPath = context.getClassesDir().resolve(generatedFile);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void preScan() {
        resetRemoteProblem();
    }

    private void resetRemoteProblem() {
        try {
            context.setRemoteProblem(null);
            // FIXME: this is a workaround for https://github.com/quarkusio/quarkus/issues/31013
        } catch (NullPointerException x) {
            // ignore
            x.printStackTrace();
        }
    }
}
