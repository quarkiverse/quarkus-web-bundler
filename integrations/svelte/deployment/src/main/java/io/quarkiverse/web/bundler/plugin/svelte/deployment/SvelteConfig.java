package io.quarkiverse.web.bundler.plugin.svelte.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.web-bundler.svelte")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface SvelteConfig {

    /**
     * Determines whether the Svelte component should be compiled as a custom element (Web Component).
     * <p>
     * When enabled, the component is wrapped in a standard Custom Element interface, allowing it to be
     * used natively in HTML without requiring the Svelte runtime.
     * </p>
     *
     * <p>
     * See: <a href="https://svelte.dev/docs/svelte/custom-elements">Custom Elements Documentation</a>
     * </p>
     *
     * <p>
     * When disabled, add the {@code org.mvnpm:svelte:provided} dependency to your project POM and include
     * the following in your {@code app.js} script to mount the component:
     * </p>
     *
     * <pre>{@code
     * import { mount } from "svelte";
     * import MyComponent from "./MyComponent.svelte";
     *
     * // Mount the component on page load in #target
     * mount(MyComponent, { target: document.querySelector("#target") });
     * }</pre>
     */
    @WithDefault("true")
    boolean customElement();

}
