package io.quarkiverse.web.bundler.runtime;

import java.util.Set;

public class WebBundlerHandlerConfig {
    public final String indexPage;
    public final boolean devMode;
    public final Set<String> compressMediaTypes;

    public WebBundlerHandlerConfig(String indexPage, boolean devMode,
            Set<String> compressMediaTypes) {
        this.indexPage = indexPage;
        this.devMode = devMode;
        this.compressMediaTypes = compressMediaTypes;
    }

}
