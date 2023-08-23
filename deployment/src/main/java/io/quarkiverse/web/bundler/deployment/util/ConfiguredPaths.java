package io.quarkiverse.web.bundler.deployment.util;

public final class ConfiguredPaths {

    public static String prefixWithSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    public static String surroundWithSlashes(String path) {
        return prefixWithSlash(addTrailingSlash(path));
    }

    public static String addTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    public static String removeLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    public static String removeTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
