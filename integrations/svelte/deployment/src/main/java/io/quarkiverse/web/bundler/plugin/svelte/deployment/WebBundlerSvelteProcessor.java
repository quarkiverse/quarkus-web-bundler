package io.quarkiverse.web.bundler.plugin.svelte.deployment;

import io.mvnpm.esbuild.plugin.EsBuildPluginSvelte;
import io.quarkiverse.web.bundler.deployment.items.ProjectRootBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerEsbuildPluginBuiltItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class WebBundlerSvelteProcessor {
    private static final String FEATURE = "web-bundler-svelte";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    WebBundlerEsbuildPluginBuiltItem initPlugin(SvelteConfig config,
            ProjectRootBuildItem projectRoot) {
        if (!projectRoot.exists()) {
            return null;
        }
        return new WebBundlerEsbuildPluginBuiltItem(new EsBuildPluginSvelte(config.customElement()));
    }

}
