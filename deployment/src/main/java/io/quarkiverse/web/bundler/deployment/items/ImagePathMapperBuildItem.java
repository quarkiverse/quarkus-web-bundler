package io.quarkiverse.web.bundler.deployment.items;

import java.util.function.Function;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * Allows extensions to define a rewrite function from source-declared paths
 * to run-time-declared paths.
 * For example, an image might be located at `Foé/some.jpg` in the sources,
 * and be placed at the `fo-/some.jpg` URI at run-time.
 */
public final class ImagePathMapperBuildItem extends SimpleBuildItem {

    private final Function<String, String> rewriter;

    public ImagePathMapperBuildItem(Function<String, String> rewriter) {
        this.rewriter = rewriter;
    }

    public String getRuntimeURI(String path) {
        return rewriter.apply(path);
    }
}
