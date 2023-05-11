package io.quarkiverse.web.bundler.runtime.qute;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class WebAssetsQuteContextRecorder {

    public static final String WEB_ASSETS_ID_PREFIX = "web-bundler/";

    public Supplier<Object> createContext(List<String> tags, Map<String, String> templates, Map<String, String> bundle) {
        return new Supplier<Object>() {
            @Override
            public Object get() {
                return new WebAssetsQuteContext() {
                    @Override
                    public List<String> tags() {
                        return tags;
                    }

                    @Override
                    public Map<String, String> templates() {
                        return templates;
                    }

                    @Override
                    public Map<String, String> bundle() {
                        return bundle;
                    }
                };
            }
        };
    }

    public interface WebAssetsQuteContext {
        List<String> tags();

        Map<String, String> templates();

        Map<String, String> bundle();
    }
}
