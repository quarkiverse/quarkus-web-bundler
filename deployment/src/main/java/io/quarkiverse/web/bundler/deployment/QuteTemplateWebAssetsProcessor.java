package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.StaticWebAssetsProcessor.makeWebAssetPublic;
import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;
import static java.util.concurrent.CompletableFuture.completedFuture;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.web.bundler.common.runtime.ImageSectionHelperFactory;
import io.quarkiverse.web.bundler.common.runtime.Images;
import io.quarkiverse.web.bundler.deployment.items.GeneratedBundleBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ImagePathMapperBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ImageSourcePathBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ImagesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteTemplatesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem.SourceType;
import io.quarkiverse.web.bundler.runtime.Bundle;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.qute.Engine;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.Expression;
import io.quarkus.qute.HtmlEscaper;
import io.quarkus.qute.ImmutableList;
import io.quarkus.qute.NamespaceResolver;
import io.quarkus.qute.Qute;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Results;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.UserTagSectionHelper;
import io.quarkus.qute.Variant;

public class QuteTemplateWebAssetsProcessor {

    @BuildStep
    void processHtmlTemplateWebAssets(WebBundlerConfig config,
            QuteTemplatesBuildItem htmlTemplates,
            WebBundlerTargetDirBuildItem targetDirBuildItem,
            GeneratedBundleBuildItem generatedBundle,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            LaunchModeBuildItem launchMode,
            BuildProducer<ImagesBuildItem> imagesBuildItemProducer,
            Optional<ImagePathMapperBuildItem> imagePathMapperBuildItem,
            List<ImageSourcePathBuildItem> imageSourcePathBuildItems) {
        final Map<String, String> bundle = generatedBundle != null ? generatedBundle.getBundle() : Map.of();
        final Bundle.Mapping mapping = new Bundle.Mapping() {
            @Override
            public String get(String name) {
                return bundle.get(name);
            }

            @Override
            public Set<String> names() {
                return bundle.keySet();
            }
        };
        Images images = new Images();
        final Engine engine = Engine.builder()
                .addDefaults()
                .addNamespaceResolver(NamespaceResolver.builder("inject")
                        .resolve((c) -> switch (c.getName()) {
                            case "bundle" -> new Bundle(mapping);
                            default -> null;
                        })
                        .build())
                .addNamespaceResolver(NamespaceResolver.builder("build")
                        .resolve((c) -> c.getName().equals("launchMode") ? launchMode.getLaunchMode().toString() : null)
                        .build())
                .addNamespaceResolver(NamespaceResolver.builder("config")
                        .resolveAsync(this::resolveConfig)
                        .build())
                .addLocator(new WebBundlerTagsLocator())
                .addSectionHelper(new UserTagSectionHelper.Factory("bundle", "web-bundler/bundle.html"))
                .addSectionHelper("image", new ImageSectionHelperFactory(images))
                .addValueResolver(new ReflectionValueResolver())
                .addParserHook(new Qute.IndexedArgumentsParserHook())
                .addResultMapper(new HtmlEscaper(ImmutableList.of("text/html", "text/xml")))
                .build();
        for (WebAsset webAsset : htmlTemplates.getWebAssets()) {
            final byte[] bytes = webAsset.resource().contentOrReadFromFile();
            Template template = engine.parse(new String(bytes, webAsset.charset()));
            String pathFromWebRoot = webAsset.pathFromWebRoot(config.webRoot());
            ImageAssetsProcessor.scanImageTags(template, images, webAsset, staticResourceProducer,
                    pathFromWebRoot, targetDirBuildItem.dist(), imagePathMapperBuildItem,
                    imageSourcePathBuildItems);
            final String content = template.render();
            makeWebAssetPublic(staticResourceProducer, prefixWithSlash(pathFromWebRoot),
                    HtmlPageWebAsset.of(webAsset, content), SourceType.BUILD_TIME_TEMPLATE);
        }
        // pass this on to any eventual runtime templates
        imagesBuildItemProducer.produce(new ImagesBuildItem(images));
    }

    private CompletionStage<Object> resolveConfig(EvalContext ctx) {
        List<Expression> params = ctx.getParams();
        final String name = ctx.getName();
        if (params.isEmpty()) {
            return findConfig(name, String.class);
        }
        if (name.equals("boolean")) {
            return ctx.evaluate(params.get(0)).thenCompose(propertyName -> findConfig(propertyName.toString(), Boolean.class));
        }
        if (name.equals("integer")) {
            return ctx.evaluate(params.get(0)).thenCompose(propertyName -> findConfig(propertyName.toString(), Integer.class));
        }
        return ctx.evaluate(params.get(0)).thenCompose(propertyName -> findConfig(propertyName.toString(), String.class));
    }

    private static <T> CompletableFuture<Object> findConfig(String propertyName, Class<T> type) {
        Optional<T> val = ConfigProvider.getConfig().getOptionalValue(propertyName, type);
        return completedFuture(val.isPresent() ? val.get() : Results.NotFound.from(propertyName));
    }

    private static final class WebBundlerTagsLocator implements TemplateLocator {
        @Override
        public Optional<TemplateLocation> locate(String id) {
            if (!id.startsWith("web-bundler/")) {
                return Optional.empty();
            }
            String name = id.replace("web-bundler/", "");
            final URL resource = this.getClass().getResource("/templates/tags/" + name);
            if (resource == null) {
                return Optional.empty();
            }
            return Optional.of(new TemplateLocation() {
                @Override
                public Reader read() {
                    try {
                        return new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public Optional<Variant> getVariant() {
                    return Optional.empty();
                }
            });
        }

    }

    record HtmlPageWebAsset(String resourceName, byte[] content, Charset charset) implements WebAsset {

        static HtmlPageWebAsset of(WebAsset sourceAsset, String content) {
            return new HtmlPageWebAsset(sourceAsset.resourceName(), content.getBytes(sourceAsset.charset()),
                    sourceAsset.charset());
        }

        @Override
        public Resource resource() {
            return new Resource(content());
        }

        @Override
        public Optional<Path> srcFilePath() {
            return Optional.empty();
        }
    }

}
