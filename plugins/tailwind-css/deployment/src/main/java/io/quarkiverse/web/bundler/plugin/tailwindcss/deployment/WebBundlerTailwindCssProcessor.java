package io.quarkiverse.web.bundler.plugin.tailwindcss.deployment;

import java.nio.file.Files;
import java.nio.file.Path;

import io.mvnpm.esbuild.plugin.EsBuildPluginTailwind;
import io.quarkiverse.web.bundler.deployment.items.ProjectRootBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerEsbuildPluginBuiltItem;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class WebBundlerTailwindCssProcessor {
    private static final String FEATURE = "web-bundler-tailwind-css";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    WebBundlerEsbuildPluginBuiltItem initPlugin(TailwindCssConfig config, ProjectRootBuildItem projectRoot,
            LaunchModeBuildItem launchMode)
            throws BuildException {
        if (!projectRoot.exists()) {
            return null;
        }
        final Path path = projectRoot.path();
        final Path base = path.resolve(config.base().orElse(""));
        if (!Files.isDirectory(base)) {
            throw new BuildException("Tailwind css requires a base path but '%s' is not a directory.".formatted(base));
        }
        return new WebBundlerEsbuildPluginBuiltItem(
                new EsBuildPluginTailwind(base.toAbsolutePath().toString().replace("\\", "/"), config.pattern(),
                        launchMode.getLaunchMode() != LaunchMode.DEVELOPMENT, true));
    }

}
