package io.quarkiverse.web.bundler.runtime;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkus.runtime.ShutdownContext;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ChangeEventHandler implements Handler<RoutingContext> {

    private final AtomicBoolean consumed = new AtomicBoolean(false);
    private final List<String> added;
    private final List<String> removed;
    private final List<String> updated;
    private final ShutdownContext shutdownContext;

    public ChangeEventHandler(List<String> added, List<String> removed, List<String> updated,
            ShutdownContext shutdownContext) {
        this.added = added;
        this.removed = removed;
        this.updated = updated;
        this.shutdownContext = shutdownContext;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        HttpServerResponse response = routingContext.response();
        response.putHeader("Content-Type", "text/event-stream");
        response.putHeader("Cache-Control", "no-cache");
        response.putHeader("Connection", "keep-alive");
        response.setChunked(true);
        response.write("event: connect\ndata: Connected\n\n");
        if (!consumed.getAndSet(true) && (!added.isEmpty() || !removed.isEmpty() || !updated.isEmpty())) {
            // Send an initial SSE event to establish the connection

            JsonObject eventData = new JsonObject();
            eventData.put("added", new JsonArray(added));
            eventData.put("removed", new JsonArray(removed));
            eventData.put("updated", new JsonArray(updated));
            response.write("event: change\ndata: " + eventData.encode() + "\n\n");
        }

        final long timerId = routingContext.vertx().setPeriodic(30000, id -> {
            if (routingContext.response().closed()) {
                routingContext.vertx().cancelTimer(id);
                return;
            }
            response.write("event: ping\n\n");
        });

        shutdownContext.addShutdownTask(() -> {
            routingContext.vertx().cancelTimer(timerId);
            if (!response.ended()) {
                response.end();
            }
        });

        routingContext.request().connection().closeHandler(v -> {
            routingContext.vertx().cancelTimer(timerId);
            if (!response.ended()) {
                response.end();
            }
        });

    }

}
