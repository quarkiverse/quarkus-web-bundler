package io.quarkiverse.web.bundler.runtime;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.quarkiverse.web.bundler.runtime.devmode.ChangeEventHandler;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.ShutdownContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.quarkus.vertx.http.runtime.HttpConfiguration;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class WebBundlerResourceRecorder {

    private final RuntimeValue<HttpConfiguration> httpConfiguration;
    private final Set<String> compressMediaTypes;

    private static volatile Function<Consumer<Set<String>>, Runnable> hotDeploymentEventHandlerRegister;
    private static volatile Runnable startWatchScheduler;

    public WebBundlerResourceRecorder(RuntimeValue<HttpConfiguration> httpConfiguration,
            HttpBuildTimeConfig httpBuildTimeConfig) {
        this.httpConfiguration = httpConfiguration;
        if (httpBuildTimeConfig.enableCompression && httpBuildTimeConfig.compressMediaTypes.isPresent()) {
            this.compressMediaTypes = Set.copyOf(httpBuildTimeConfig.compressMediaTypes.get());
        } else {
            this.compressMediaTypes = Set.of();
        }
    }

    public Handler<RoutingContext> createHandler(final String directory,
            final Set<String> webResources, boolean devMode) {
        final var handlerConfig = new WebBundlerHandlerConfig(httpConfiguration.getValue().staticResources.indexPage, devMode,
                compressMediaTypes);
        return new WebBundlerResourceHandler(handlerConfig, directory,
                webResources);
    }

    public Handler<RoutingContext> createChangeEventHandler(final String webResourcesDirectory,
            String webRoot,
            final Set<String> webResources,
            ShutdownContext shutdownContext) {
        startWatchScheduler.run();
        return new ChangeEventHandler(hotDeploymentEventHandlerRegister, webResourcesDirectory, webRoot,
                webResources,
                shutdownContext);
    }

    public static void setHotDeploymentEventHandlerRegister(
            Function<Consumer<Set<String>>, Runnable> hotDeploymentEventHandlerRegister) {
        WebBundlerResourceRecorder.hotDeploymentEventHandlerRegister = hotDeploymentEventHandlerRegister;
    }

    public static void setStartWatchScheduler(Runnable startWatchScheduler) {
        WebBundlerResourceRecorder.startWatchScheduler = startWatchScheduler;
    }

}
