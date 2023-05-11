package io.quarkiverse.web.bundler.deployment.items;

import static io.quarkiverse.web.bundler.deployment.ProjectResourcesScanner.readTemplateContent;
import static java.util.Objects.requireNonNull;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class WebAsset {

    private final String resourcePath;
    private final Optional<Path> filePath;
    private final byte[] content;

    private final Charset charset;

    public WebAsset(String resourcePath, Path filePath, Charset charset) {
        this(resourcePath, Optional.of(filePath), null, charset);
    }

    public WebAsset(String resourcePath, Optional<Path> filePath, byte[] content, Charset charset) {
        this.resourcePath = requireNonNull(resourcePath, "resourcePath is required");
        this.filePath = requireNonNull(filePath, "filePath is required");
        this.content = content;
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

    public String pathFromWebRoot(String root) {
        if (!getResourceName().startsWith(root)) {
            throw new IllegalStateException("Web Bundler must be located under the root: " + root);
        }
        return getResourceName().substring(root.endsWith("/") ? root.length() : root.length() + 1);
    }

    /**
     * Uses the system-dependent path separator.
     *
     * @return the full path of the asset
     */
    public Optional<Path> getFilePath() {
        return filePath;
    }

    public byte[] getContent() {
        return this.content;
    }

    public byte[] readContentFromFile() {
        return readTemplateContent(filePath.orElseThrow());
    }

    public boolean hasContent() {
        return this.content != null;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean matches(String glob) {
        if (!getFilePath().isPresent()) {
            return false;
        }
        return getFilePath().get().getFileSystem().getPathMatcher(glob).matches(getFilePath().get());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WebAsset webAsset = (WebAsset) o;
        return resourcePath.equals(webAsset.resourcePath) && filePath.equals(webAsset.filePath) && Arrays.equals(content,
                webAsset.content) && charset.equals(webAsset.charset);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(resourcePath, filePath, charset);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
