package io.quarkiverse.web.bundler.runtime.qute;

import java.util.Map;

import io.quarkus.qute.TemplateGlobal;

@TemplateGlobal
public class WebBundlerQuteTemplateGlobal {

    /**
     * This is for Qute auto-completion detection
     * Actual instance is set in WebBundlerQuteEngineObserver
     */
    public static final Map<String, String> WEB_BUNDLER_BUILD_MAPPING = Map.of();

}
