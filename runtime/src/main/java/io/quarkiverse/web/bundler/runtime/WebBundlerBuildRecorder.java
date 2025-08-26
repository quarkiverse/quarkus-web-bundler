package io.quarkiverse.web.bundler.runtime;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class WebBundlerBuildRecorder {

    public Supplier<?> createContext(Map<String, String> bundle) {
        return new Supplier<Bundle.Mapping>() {
            @Override
            public Bundle.Mapping get() {
                return new Bundle.Mapping() {
                    @Override
                    public String get(String name) {
                        return bundle.get(name);
                    }

                    @Override
                    public Set<String> names() {
                        return bundle.keySet();
                    }
                };
            }

        };
    }
}
