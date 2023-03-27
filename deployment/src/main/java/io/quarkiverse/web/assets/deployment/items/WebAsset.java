package io.quarkiverse.web.assets.deployment.items;

import static java.util.Objects.requireNonNull;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

public final class WebAsset {

    private final String resourcePath;
    private final Path filePath;
    private final byte[] content;

    private final Charset charset;

    public WebAsset(String resourcePath, Path filePath, byte[] content, Charset charset) {
        this.resourcePath = requireNonNull(resourcePath, "resourcePath is required");
        this.filePath = requireNonNull(filePath, "filePath is required");
        this.content = requireNonNull(content, "content is required");
        this.charset = requireNonNull(charset, "charset is required");
    }

    /**
     * Uses the {@code /} path separator.
     *
     * @return the path relative to the asset root
     */
    public String getResourceName() {
        return resourcePath;
    }

    /**
     * Uses the system-dependent path separator.
     *
     * @return the full path of the asset
     */
    public Path getFilePath() {
        return filePath;
    }

    public byte[] getContent() {
        return content;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean matches(String glob) {
        return getFilePath().getFileSystem().getPathMatcher(glob).matches(getFilePath());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WebAsset webAsset = (WebAsset) o;
        return Objects.equals(resourcePath, webAsset.resourcePath) && Objects.equals(filePath,
                webAsset.filePath) && Arrays.equals(content, webAsset.content)
                && Objects.equals(charset,
                        webAsset.charset);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(resourcePath, filePath, charset);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
