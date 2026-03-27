package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.nio.file.Path;

public final class LocalProjectFile extends LazyContentProjectFile {

    public LocalProjectFile(String indexPath, String scopedPath, Path path, Charset charset) {
        super(indexPath, scopedPath, path, Origin.LOCAL_PROJECT_FILE, charset);
    }

    @Override
    public boolean isSrcFile() {
        return true;
    }

    @Override
    public String watchPath() {
        return path().toString();
    }

}
