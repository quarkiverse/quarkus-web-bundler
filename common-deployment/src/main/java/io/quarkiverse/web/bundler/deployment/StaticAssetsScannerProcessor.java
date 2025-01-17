package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.items.PublicAssetsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkus.deployment.annotations.BuildStep;

public class StaticAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(StaticAssetsScannerProcessor.class);

    @BuildStep
    PublicAssetsBuildItem scan(ProjectResourcesScannerBuildItem scanner,
            WebBundlerConfig config)
            throws IOException {

        LOGGER.debug("Web Bundler scan - public assets: start");

        final List<WebAsset> assets = new ArrayList<>(scanner
                .scan(new ProjectResourcesScannerBuildItem.Scanner("public",
                        "glob:**", config.getEffectiveIgnoredFiles(), config.charset())));
        LOGGER.debugf("\"Web Bundler scan - public assets: %d found.", assets.size());

        //Fallback
        LOGGER.debug("Web Bundler scan - static assets: start");
        final List<WebAsset> staticAssets = scanner
                .scan(new ProjectResourcesScannerBuildItem.Scanner("static",
                        "glob:**", config.getEffectiveIgnoredFiles(), config.charset()));
        assets.addAll(staticAssets);
        LOGGER.debugf("\"Web Bundler scan - static assets (fallback): %d found.", staticAssets.size());

        return new PublicAssetsBuildItem(assets);
    }

    private record StaticAssetsContext(WebBundlerConfig config, List<WebAsset> assets) {
    }
}
