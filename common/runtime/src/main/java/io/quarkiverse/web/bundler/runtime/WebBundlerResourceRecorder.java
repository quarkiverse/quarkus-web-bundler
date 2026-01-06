package io.quarkiverse.web.bundler.runtime;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkiverse.web.bundler.runtime.devmode.ChangeEventHandler;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class WebBundlerResourceRecorder {

    private static volatile Function<Consumer<Set<String>>, Runnable> hotDeploymentEventHandlerRegister;

    public Handler<RoutingContext> createChangeEventHandler(final String webResourcesDirectory,
            String webRoot,
            List<String> localDirs,
            final Set<String> webResources,
            ShutdownContext shutdownContext) {
        return new ChangeEventHandler(hotDeploymentEventHandlerRegister, webResourcesDirectory, webRoot,
                localDirs, shutdownContext, webResources);
    }

    public static void setHotDeploymentEventHandlerRegister(
            Function<Consumer<Set<String>>, Runnable> hotDeploymentEventHandlerRegister) {
        WebBundlerResourceRecorder.hotDeploymentEventHandlerRegister = hotDeploymentEventHandlerRegister;
    }

}
