package io.quarkiverse.web.bundler.deployment.devui;

import static io.quarkiverse.web.bundler.deployment.devui.DevUIWebDependenciesBuildItem.DevUIWebDependency;
import static io.quarkiverse.web.bundler.deployment.devui.DevUIWebDependenciesBuildItem.WebDependencyAsset;
import static io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourcesProcessor.resolveFromRootPath;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.WebDependenciesBuildItem;
import io.quarkiverse.web.bundler.deployment.util.PathUtils;
import io.quarkus.bootstrap.classloading.ClassPathElement;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.LiveReloadBuildItem;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;

public class WebBundlerDevUIWebDependenciesProcessor {

    private static final String PREFIX = "META-INF/resources/";
    private static final String WEBJARS_PATH = "webjars";
    private static final String MVNPM_PATH = "_static";

    private static final Logger log = Logger.getLogger(WebBundlerDevUIWebDependenciesProcessor.class.getName());

    @BuildStep(onlyIf = IsDevelopment.class)
    public DevUIWebDependenciesBuildItem findWebDependenciesAssets(
            HttpBuildTimeConfig httpConfig,
            LiveReloadBuildItem liveReload,
            WebDependenciesBuildItem webDependencies) {
        final DevUIWebDependenciesContext webDependenciesContext = liveReload
                .getContextObject(DevUIWebDependenciesContext.class);

        if (liveReload.isLiveReload() && webDependenciesContext != null
                && webDependenciesContext.dependencies().equals(webDependencies.list())) {
            return new DevUIWebDependenciesBuildItem(webDependenciesContext.devUIWebDependencies());
        }

        final List<ClassPathElement> providers = new ArrayList<>();
        providers.addAll(QuarkusClassLoader.getElements(PREFIX + MVNPM_PATH, false));
        providers.addAll(QuarkusClassLoader.getElements(PREFIX + WEBJARS_PATH, false));

        if (!providers.isEmpty()) {
            // Map of webDependency artifact keys to class path elements
            final Map<ArtifactKey, ClassPathElement> providersByKeys = providers.stream()
                    .filter(provider -> provider.getDependencyKey() != null)
                    .collect(Collectors.toMap(ClassPathElement::getDependencyKey, provider -> provider, (a, b) -> b,
                            () -> new HashMap<>(providers.size())));

            final List<DevUIWebDependency> webJarDeps = new ArrayList<>(webDependencies.list().size());
            for (WebDependenciesBuildItem.Dependency dependency : webDependencies.list()) {
                final DevUIWebDependency dep = getDep(httpConfig, providersByKeys, dependency);
                if (dep != null) {
                    webJarDeps.add(dep);
                }
            }
            liveReload.setContextObject(DevUIWebDependenciesContext.class,
                    new DevUIWebDependenciesContext(webDependencies.list(), webJarDeps));
            return new DevUIWebDependenciesBuildItem(webJarDeps);
        }
        liveReload.setContextObject(DevUIWebDependenciesContext.class, new DevUIWebDependenciesContext());
        return new DevUIWebDependenciesBuildItem(List.of());
    }

    private DevUIWebDependency getDep(HttpBuildTimeConfig httpConfig, Map<ArtifactKey, ClassPathElement> providersByKeys,
            WebDependenciesBuildItem.Dependency webDependency) {
        String path = getTypePath(webDependency);
        final String webDependencyRootPath = PathUtils.addTrailingSlash(resolveFromRootPath(httpConfig, path));

        return createWebDependency(webDependency, webDependencyRootPath, providersByKeys, path);
    }

    private static String getTypePath(WebDependenciesBuildItem.Dependency webDependency) {
        return switch (webDependency.type()) {
            case MVNPM -> MVNPM_PATH;
            case WEBJARS -> WEBJARS_PATH;
        };
    }

    private DevUIWebDependency createWebDependency(WebDependenciesBuildItem.Dependency webDep,
            String webDependencyRootPath,
            Map<ArtifactKey, ClassPathElement> providersByKeys,
            String path) {
        final ResolvedDependency dep = webDep.resolvedDependency();
        final ClassPathElement provider = providersByKeys.get(dep.getKey());
        if (provider == null) {
            return null;
        }
        return provider.apply(tree -> {
            final Path webDependenciesDir = tree.getPath(PREFIX + path);
            final Path nameDir;
            try (Stream<Path> webDependenciesDirPaths = Files.list(webDependenciesDir)) {
                nameDir = webDependenciesDirPaths.filter(Files::isDirectory).findFirst().orElseThrow(() -> new IOException(
                        "Could not find name directory for " + dep.getKey().getArtifactId() + " in " + webDependenciesDir));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            final Path versionDir;
            Path root = nameDir;
            // The base URL for the Web Dependency
            final StringBuilder urlBase = new StringBuilder(webDependencyRootPath);
            boolean appendRootPart = true;
            try {
                // If the version directory exists, use it as a root, otherwise use the name directory
                versionDir = nameDir.resolve(dep.getVersion());
                root = Files.isDirectory(versionDir) ? versionDir : nameDir;
                urlBase.append(nameDir.getFileName().toString())
                        .append("/");
                appendRootPart = false;
            } catch (InvalidPathException e) {
                log.warn("Could not find version directory for " + dep.getKey().getArtifactId() + " "
                        + dep.getVersion() + " in " + nameDir + ", falling back to name directory");
            }
            try {
                // Create the asset tree for the web dependency and set it as the root asset
                var asset = createAssetForDep(root, urlBase.toString(), appendRootPart);
                return new DevUIWebDependency(webDep.type().toString(),
                        provider.getDependencyKey().getGroupId() + ":" + provider.getDependencyKey().getArtifactId(),
                        dep.getVersion(), asset);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private WebDependencyAsset createAssetForDep(Path rootPath, String urlBase, boolean appendRootPart)
            throws IOException {
        //If it is a directory, go deeper, otherwise add the file
        urlBase = appendRootPart ? urlBase + rootPath.getFileName().toString() + "/" : urlBase;
        var root = new WebDependencyAsset(rootPath.getFileName().toString(),
                new LinkedList<>(),
                false,
                urlBase);

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(rootPath)) {
            for (Path childPath : directoryStream) {
                if (Files.isDirectory(childPath)) { // If it is a directory, go deeper, otherwise add the file
                    var childDir = createAssetForDep(childPath, urlBase, true);
                    root.children().add(childDir);
                } else {
                    var childFile = new WebDependencyAsset(childPath.getFileName().toString(),
                            null,
                            true,
                            urlBase + childPath.getFileName());
                    root.children().add(childFile);
                }
            }
        }
        // Sort the children by name
        root.children().sort(Comparator.comparing(WebDependencyAsset::fileAsset).thenComparing(WebDependencyAsset::name));
        return root;
    }

    record DevUIWebDependenciesContext(List<WebDependenciesBuildItem.Dependency> dependencies,
            List<DevUIWebDependency> devUIWebDependencies) {

        DevUIWebDependenciesContext() {
            this(null, null);
        }

    }

}
