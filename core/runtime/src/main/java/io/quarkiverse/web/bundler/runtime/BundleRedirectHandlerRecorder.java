package io.quarkiverse.web.bundler.runtime;

import java.util.Map;
import java.util.Objects;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class BundleRedirectHandlerRecorder {

    public Handler<RoutingContext> handler(Map<String, String> bundle) {
        return event -> {
            final String path = event.normalizedPath();
            final String entryPoint = path.substring(path.lastIndexOf('/') + 1);
            if (!bundle.containsKey(entryPoint) || Objects.equals(bundle.get(entryPoint), path)) {
                event.next();
                return;
            }
            event.response().setStatusCode(302);
            event.response().putHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            event.response().putHeader("Location", bundle.get(entryPoint));
            event.response().end();
        };
    }

}
