package io.quarkiverse.web.bundler.runtime;

import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("bundled")
public class Bundled {

    private final Mapping mapping;

    @Inject
    public Bundled(Mapping mapping) {
        this.mapping = mapping;
    }

    public Mapping mapping() {
        return mapping;
    }

    /**
     * Resolve the script file public path including the hash and extension from the entry point key.
     *
     * @param key the fixed entry point key (eg.: main)
     * @return the file public path (eg.: static/main-HE233H4.js)
     */
    public String script(String key) {
        return mapping.get(key + ".js");
    }

    public String resolve(String name) {
        return mapping.get(name);
    }

    /**
     * Resolve the style file public path including the hash and extension from the entry point key.
     *
     * @param key the fixed entry point key (eg.: main)
     * @return the file public path (eg.: static/main-HE233H4.css)
     */
    public String style(String key) {
        return mapping.get(key + ".css");
    }

    public interface Mapping {
        String get(String name);

        Set<String> names();
    }
}
