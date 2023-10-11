package io.quarkiverse.web.bundler.runtime;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class WebDependenciesBlockerRecorder {

    public Handler<RoutingContext> handler() {
        return event -> {
            event.response().setStatusCode(404);
            event.response().send();
        };
    }
}
