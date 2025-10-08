package io.quarkiverse.web.bundler.runtime;

import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import io.vertx.core.json.JsonObject;

@Named("bundle")
public class Bundle {

    /**
     * Other extensions from the bundle might have duplicate names (but different hash), so we don't include them in the
     * mapping.
     */
    public static final Set<String> BUNDLE_MAPPING_EXT = Set.of(".js", ".css", ".js.map", ".css.map");

    private final Mapping mapping;

    @Inject
    public Bundle(Mapping mapping) {
        this.mapping = mapping;
    }

    public Mapping mapping() {
        return mapping;
    }

    public String importMap() {
        final JsonObject imports = new JsonObject();

        for (String name : mapping.names()) {
            if (name.endsWith(".js") && !name.contains("chunk")) {
                imports.put(name, mapping.get(name));
                imports.put(name.replace(".js", ""), mapping.get(name));
            }
        }

        return new JsonObject().put("imports", imports).toString();
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
