package io.quarkiverse.web.bundler.plugin.tailwindcss.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import io.mvnpm.esbuild.plugin.EsBuildPluginTailwind;
import io.quarkiverse.web.bundler.deployment.items.ProjectRootBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerEsbuildPluginBuiltItem;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;

public class WebBundlerTailwindCssProcessor {
    private static final Logger LOGGER = Logger.getLogger(WebBundlerTailwindCssProcessor.class);
    private static final String FEATURE = "web-bundler-tailwindcss";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    @Produce(WebBundlerEsbuildPluginBuiltItem.class)
    public void addTemplateContentForScanning(
            TailwindCssConfig config,
            WebBundlerTargetDirBuildItem targetDirBuildItem,
            BuildProducer<WebBundlerTailwindCssSourceContentBuildItem> tailwindContentProducer) {
        if (targetDirBuildItem == null) {
            return;
        }
        QuarkusClassLoader.visitRuntimeResources("templates", p -> {
            // theme templates are in a jar, let's make sure they are scanned
            try (var paths = Files.find(p.getPath(), Integer.MAX_VALUE, tailwindContentFilter(config))) {
                paths.forEach(path -> {
                    try {
                        tailwindContentProducer
                                .produce(new WebBundlerTailwindCssSourceContentBuildItem(Files.readString(path)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        });
    }

    public static BiPredicate<Path, BasicFileAttributes> tailwindContentFilter(TailwindCssConfig config) {
        return (path, basicFileAttributes) -> {
            return path.getFileSystem().getPathMatcher("glob:" + config.pattern())
                    .matches(path);
        };
    }

    @BuildStep
    @Produce(WebBundlerEsbuildPluginBuiltItem.class)
    public void processContent(WebBundlerTargetDirBuildItem targetDirBuildItem,
            List<WebBundlerTailwindCssSourceContentBuildItem> contentList,
            BuildProducer<WebBundlerTailwindCssSourceBuildItem> sourceProducer) {
        if (targetDirBuildItem == null) {
            return;
        }
        final Path targetDir = targetDirBuildItem.webBundler().resolve("scan");
        sourceProducer.produce(
                new WebBundlerTailwindCssSourceBuildItem(targetDir.toString(), "**/*.html", false));
        final String merged = contentList.stream().map(WebBundlerTailwindCssSourceContentBuildItem::content)
                .collect(Collectors.joining("\n\n---\n\n"));
        try {
            Files.createDirectories(targetDir);
            Files.writeString(targetDir.resolve("extensions-merge-content.html"), merged, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BuildStep
    WebBundlerEsbuildPluginBuiltItem initPlugin(TailwindCssConfig config,
            ProjectRootBuildItem projectRoot,
            LaunchModeBuildItem launchMode,
            List<WebBundlerTailwindCssSourceBuildItem> sources)
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
                new EsBuildPluginTailwind(
                        new EsBuildPluginTailwind.Source(base.toAbsolutePath().toString(), config.pattern(), false),
                        sources.stream().map(WebBundlerTailwindCssSourceBuildItem::source).toList(),
                        launchMode.getLaunchMode() != LaunchMode.DEVELOPMENT, true));
    }

}
