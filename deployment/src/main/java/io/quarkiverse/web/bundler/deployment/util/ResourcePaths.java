package io.quarkiverse.web.bundler.deployment.util;

public final class ResourcePaths {

    public static String join(String dir1, String dir2) {
        if (dir1 == null || dir1.isEmpty() || "/".equals(dir1)) {
            return dir2;
        }
        if (dir2 == null || dir2.isEmpty()) {
            return dir1;
        }
        return (dir1.endsWith("/") ? dir1.substring(0, dir1.length() - 1) : dir1)
                + "/"
                + (dir2.startsWith("/") ? dir2.substring(1) : dir2);
    }

    public static String prefixWithSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
