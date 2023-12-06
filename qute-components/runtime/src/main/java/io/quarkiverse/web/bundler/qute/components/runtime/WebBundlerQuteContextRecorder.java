package io.quarkiverse.web.bundler.qute.components.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class WebBundlerQuteContextRecorder {

    public static final String WEB_BUNDLER_ID_PREFIX = "web-bundler/";

    public Supplier<?> createContext(List<String> tags, Map<String, String> templates) {
        return new Supplier<Object>() {
            @Override
            public WebBundlerQuteContext get() {
                return new WebBundlerQuteContext() {
                    @Override
                    public List<String> tags() {
                        return tags;
                    }

                    @Override
                    public Map<String, String> templates() {
                        return templates;
                    }

                };
            }
        };
    }

    public interface WebBundlerQuteContext {
        List<String> tags();

        Map<String, String> templates();
    }
}
