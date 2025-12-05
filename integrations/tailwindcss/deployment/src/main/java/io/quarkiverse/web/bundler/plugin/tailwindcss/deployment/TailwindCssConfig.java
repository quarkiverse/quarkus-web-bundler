package io.quarkiverse.web.bundler.plugin.tailwindcss.deployment;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.web-bundler.tailwindcss")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface TailwindCssConfig {

    /**
     * Base directory for tailwind css relative to the project root.
     */
    @ConfigDocDefault("the project root")
    Optional<String> base();

    /**
     * The default glob pattern to scan in the project
     */
    @WithDefault("**/*.{html,md,adoc,markdown,asciidoc}")
    String pattern();

}
