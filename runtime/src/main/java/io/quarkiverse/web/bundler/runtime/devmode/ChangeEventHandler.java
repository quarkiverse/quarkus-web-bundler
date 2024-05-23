package io.quarkiverse.web.bundler.runtime.devmode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import org.jboss.logging.Logger;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.runtime.ShutdownContext;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ChangeEventHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = Logger.getLogger(ChangeEventHandler.class);

    private static final List<String> IGNORED_SUFFIX = List.of(".map");
    public static final String MEDIA_TYPE_TEXT_EVENT_STREAM = "text/event-stream";
    private final Map<String, Long> lastModifiedMap;
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private final ClassLoader cl;
    private final Path directory;
    private final Runnable unRegisterChangeListener;

    public ChangeEventHandler(Function<Consumer<Set<String>>, Runnable> registerHandler, String directory,
            Set<String> webResources, ShutdownContext shutdownContext) {
        this.directory = Path.of(directory);
        this.lastModifiedMap = initLastModifiedMap(webResources);
        this.cl = Thread.currentThread().getContextClassLoader();
        this.unRegisterChangeListener = registerHandler.apply(this::onChange);
        shutdownContext.addShutdownTask(this::onShutdown);
    }

    private void onShutdown() {
        final ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(cl);
        try {
            unRegisterChangeListener.run();
            for (Connection connection : connections) {
                closeConnection(connection);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
        }

    }

    private Map<String, Long> initLastModifiedMap(Set<String> webResources) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(directory + " should exist on disk.");
        }
        HashMap<String, Long> map = new HashMap<>();
        try {
            for (String webResource : webResources) {
                final String relativePath = webResource.substring(1);
                if (matches(IGNORED_SUFFIX, relativePath)) {
                    continue;
                }
                map.put(webResource, Files.getLastModifiedTime(directory.resolve(relativePath)).toMillis());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ConcurrentHashMap<>(map);
    }

    private void onChange(Set<String> srcChanges) {
        final boolean isBundlingError = srcChanges.contains("web-bundler/build-error");
        if (!srcChanges.contains("web-bundler/build-success") && !isBundlingError) {
            return;
        }
        final ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(cl);
        try {
            final Changes changes = computeChanges();

            LOGGER.info(changes);
            for (Connection connection : connections) {
                if (!connection.closed().get() && !connection.ctx().response().closed()) {
                    if (isBundlingError) {
                        connection.ctx.response().write("event: bundling-error\ndata:\n\n");
                        continue;
                    }
                    if (!changes.added.isEmpty() || !changes.removed.isEmpty() || !changes.updated.isEmpty()) {
                        // Send an initial SSE event to establish the connection
                        JsonObject eventData = new JsonObject();
                        eventData.put("added", new JsonArray(changes.added));
                        eventData.put("removed", new JsonArray(changes.removed));
                        eventData.put("updated", new JsonArray(changes.updated));
                        connection.ctx.response().write("event: change\ndata: " + eventData.encode() + "\n\n");
                    }

                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(oldCl);
        }

    }

    private Changes computeChanges() {
        // added files will require a restart, no need to look for them
        final List<String> updated = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
        for (String key : lastModifiedMap.keySet()) {
            lastModifiedMap.compute(key, (k, prevLastModified) -> {
                final String relativePath = k.substring(1);
                final Path file = directory.resolve(relativePath);
                if (!Files.isRegularFile(file)) {
                    removed.add(k);
                    return null;
                } else {
                    final long lastModified;
                    try {
                        lastModified = Files.getLastModifiedTime(file).toMillis();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    if (prevLastModified == null) {
                        return lastModified;
                    }
                    if (lastModified > prevLastModified) {
                        updated.add(k);
                        return lastModified;
                    }
                    return lastModified;
                }
            });
        }

        return new Changes(List.of(), removed, updated);
    }

    @Override
    public void handle(RoutingContext routingContext) {

        if (connections.size() > 2) {
            routingContext.response().setStatusCode(HttpResponseStatus.TOO_MANY_REQUESTS.code());
            routingContext.response().send();
            return;
        }

        final String header = routingContext.request().getHeader(HttpHeaders.ACCEPT);
        if (header == null || !header.equalsIgnoreCase(MEDIA_TYPE_TEXT_EVENT_STREAM)) {
            routingContext.response().setStatusCode(HttpResponseStatus.OK.code());
            routingContext.response().send();
            return;
        }

        HttpServerResponse response = routingContext.response();
        response.putHeader("Content-Type", MEDIA_TYPE_TEXT_EVENT_STREAM);
        response.putHeader("Cache-Control", "no-cache");
        response.putHeader("Connection", "keep-alive");
        response.setChunked(true);
        response.write("event: connect\ndata: Connected\n\n");

        final long timerId = routingContext.vertx().setPeriodic(30000, id -> {
            if (routingContext.response().closed()) {
                routingContext.vertx().cancelTimer(id);
                return;
            }
            response.write("event: ping\n\n");
        });

        final Connection connection = new Connection(routingContext, timerId);
        connections.add(connection);

        routingContext.request().connection().closeHandler(v -> {
            closeConnection(connection);
        });
    }

    private void closeConnection(Connection connection) {
        connections.remove(connection);
        if (!connection.closed.getAndSet(true)) {
            connection.ctx().vertx().cancelTimer(connection.timerId());
            if (!connection.ctx().response().ended()) {
                connection.ctx().response().end();
            }
        }
    }

    static boolean matches(List<String> suffixes, String name) {
        for (String suffix : suffixes) {
            if (name.toLowerCase().endsWith(suffix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    record Connection(RoutingContext ctx, long timerId, AtomicBoolean closed) {
        Connection(RoutingContext ctx, long timerId) {
            this(ctx, timerId, new AtomicBoolean(false));
        }
    }

    record Changes(List<String> added, List<String> removed, List<String> updated) {
    }

}
