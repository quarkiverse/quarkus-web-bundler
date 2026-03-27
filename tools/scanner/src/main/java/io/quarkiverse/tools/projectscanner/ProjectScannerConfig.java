package io.quarkiverse.tools.projectscanner;

import java.nio.charset.Charset;
import java.util.List;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.project-scanner")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface ProjectScannerConfig {

    String IGNORED_FILES = "glob:**.DS_Store,glob:**Thumbs.db,glob:**.*~,glob:**.class";

    /**
     * The default set of ignored files during project resources and files indexing.
     * <p>
     * Entries may be prefixed with {@code regex:} or {@code glob:}.
     * The default ignored files include:
     * <ul>
     * <li><code>glob:**.DS_Store</code></li>
     * <li><code>glob:**Thumbs.db</code></li>
     * <li><code>glob:**.*~</code></li>
     * <li><code>glob:**.class</code></li>
     * </ul>
     */
    @WithDefault(IGNORED_FILES)
    List<String> defaultIgnoredFiles();

    /**
     * The default charset used when reading web assets.
     */
    @WithDefault("UTF-8")
    Charset charset();

    /**
     * Warning threshold for the number of indexed files.
     * If the scanner indexes more files than this limit, a warning is logged.
     * Increase this value if your application legitimately has a large number of scanned resources.
     */
    @WithDefault("10000")
    int indexedFilesWarningThreshold();
}