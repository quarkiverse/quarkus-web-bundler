package io.quarkiverse.web.bundler.deployment.staticresources;

import java.util.Set;

public class StaticResourcesDevContext {

    private final Set<String> generatedStaticResourceNames;

    public StaticResourcesDevContext(Set<String> generatedStaticResourceNames) {
        this.generatedStaticResourceNames = generatedStaticResourceNames;
    }

    public Set<String> getGeneratedStaticResourceNames() {
        return generatedStaticResourceNames;
    }
}
