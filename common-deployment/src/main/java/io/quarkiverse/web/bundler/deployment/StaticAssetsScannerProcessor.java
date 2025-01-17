package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.items.StaticAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkus.deployment.annotations.BuildStep;

public class StaticAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(StaticAssetsScannerProcessor.class);

    @BuildStep
    StaticAssetsBuildItem scan(ProjectResourcesScannerBuildItem scanner,
            WebBundlerConfig config)
            throws IOException {

        LOGGER.debug("Web Bundler scan - static assets: start");
        final List<WebAsset> assets = scanner
                .scan(new ProjectResourcesScannerBuildItem.Scanner(config.staticDir(),
                        "glob:**", config.charset()));
        LOGGER.debugf("\"Web Bundler scan - static assets: %d found.", assets.size());
        return new StaticAssetsBuildItem(assets);
    }

    private record StaticAssetsContext(WebBundlerConfig config, List<WebAsset> assets) {
    }
}
