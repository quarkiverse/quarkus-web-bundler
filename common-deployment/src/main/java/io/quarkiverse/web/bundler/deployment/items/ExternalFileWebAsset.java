package io.quarkiverse.web.bundler.deployment.items;

import java.nio.charset.Charset;
import java.nio.file.Path;

public final class ExternalFileWebAsset extends LocalFileWebAsset {

    public ExternalFileWebAsset(String webPath, Path path, Charset charset) {
        super(webPath, path, Type.EXTERNAL, charset);
    }

    @Override
    public String watchPath() {
        return path().toString();
    }
}
