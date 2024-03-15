package io.quarkiverse.web.bundler.runtime;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.jboss.logging.Logger;

import io.quarkus.runtime.util.StringUtil;
import io.quarkus.vertx.http.runtime.RouteConstants;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.MimeMapping;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;

public class WebBundlerResourceHandler implements Handler<RoutingContext> {

    public static final Set<HttpMethod> HANDLED_METHODS = Set.of(HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.GET);
    private static final Logger LOG = Logger.getLogger(WebBundlerResourceHandler.class);

    public static final String META_INF_WEB = "META-INF/web";
    public static int DEFAULT_ROUTE_ORDER = RouteConstants.ROUTE_ORDER_BEFORE_DEFAULT + 50;

    private final WebBundlerHandlerConfig config;
    private final Set<String> webResources;
    private final Handler<RoutingContext> handler;
    private final ClassLoader currentClassLoader;

    WebBundlerResourceHandler(final WebBundlerHandlerConfig config, final String directory, final Set<String> webResources) {
        this.config = config;
        this.webResources = encodeUIResources(webResources);
        handler = createStaticHandler(config, directory);
        currentClassLoader = Thread.currentThread().getContextClassLoader();
    }

    private static Set<String> encodeUIResources(Set<String> uiResources) {
        Set<String> encodedUIResources = new HashSet<>(uiResources.size());
        for (String uiResource : uiResources) {
            encodedUIResources.add(encodeURI(uiResource));
        }
        return encodedUIResources;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!shouldHandleMethod(ctx)) {
            next(currentClassLoader, ctx);
            return;
        }
        final String path = resolvePath(ctx);
        final String resourcePath = path.endsWith("/") ? path + config.indexPage : path;
        LOG.debugf("Quinoa is checking: '%s'", resourcePath);
        if (webResources.contains(resourcePath)) {
            LOG.debugf("Quinoa is serving: '%s'", resourcePath);
            compressIfNeeded(config, ctx, resourcePath);
            handler.handle(ctx);
        } else {
            next(currentClassLoader, ctx);
        }
    }

    private static Handler<RoutingContext> createStaticHandler(WebBundlerHandlerConfig config, String directory) {
        LOG.debugf("Static Index: '%s'", config.indexPage);
        if (StringUtil.isNullOrEmpty(config.indexPage)) {
            throw new IllegalStateException("Static index page is not configured!");
        }
        final StaticHandler staticHandler = directory != null ? StaticHandler.create(FileSystemAccess.ROOT, directory)
                : StaticHandler.create(META_INF_WEB);
        staticHandler.setDefaultContentEncoding(StandardCharsets.UTF_8.name());
        staticHandler.setIndexPage(config.indexPage);
        staticHandler.setCachingEnabled(!config.devMode);
        return staticHandler;
    }

    /**
     * Duplicate code from OmniFaces project under apache license:
     * https://github.com/omnifaces/omnifaces/blob/develop/license.txt
     * <p>
     * URI-encode the given string using UTF-8. URIs (paths and filenames) have different encoding rules as compared to
     * URL query string parameters. {@link URLEncoder} is actually only for www (HTML) form based query string parameter
     * values (as used when a webbrowser submits a HTML form). URI encoding has a lot in common with URL encoding, but
     * the space has to be %20 and some chars doesn't necessarily need to be encoded.
     *
     * @param string The string to be URI-encoded using UTF-8.
     * @return The given string, URI-encoded using UTF-8, or <code>null</code> if <code>null</code> was given.
     */
    private static String encodeURI(String string) {
        if (string == null) {
            return null;
        }

        return URLEncoder.encode(string, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%2F", "/")
                .replace("%7E", "~");
    }

    static String resolvePath(RoutingContext ctx) {
        return (ctx.mountPoint() == null) ? ctx.normalizedPath()
                : ctx.normalizedPath().substring(
                        // let's be extra careful here in case Vert.x normalizes the mount points at
                        // some point
                        ctx.mountPoint().endsWith("/") ? ctx.mountPoint().length() - 1 : ctx.mountPoint().length());
    }

    static void compressIfNeeded(WebBundlerHandlerConfig config, RoutingContext ctx, String path) {
        if (isCompressed(config, path)) {
            // VertxHttpRecorder is adding "Content-Encoding: identity" to all requests if
            // compression is enabled.
            // Handlers can remove the "Content-Encoding: identity" header to enable
            // compression.
            ctx.response().headers().remove(HttpHeaders.CONTENT_ENCODING);
        }
    }

    private static boolean isCompressed(WebBundlerHandlerConfig config, String path) {
        if (config.compressMediaTypes.isEmpty()) {
            return false;
        }
        String contentType = MimeMapping.getMimeTypeForFilename(path);
        return contentType != null && config.compressMediaTypes.contains(contentType);
    }

    static boolean shouldHandleMethod(RoutingContext ctx) {
        return HANDLED_METHODS.contains(ctx.request().method());
    }

    static void next(ClassLoader cl, RoutingContext ctx) {
        // make sure we don't lose the correct TCCL to Vert.x...
        Thread.currentThread().setContextClassLoader(cl);
        ctx.next();
    }

}
