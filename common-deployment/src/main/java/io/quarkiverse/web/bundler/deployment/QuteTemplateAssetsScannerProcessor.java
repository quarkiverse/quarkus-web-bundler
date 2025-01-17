package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.ProjectResourcesScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteTemplatesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkus.deployment.annotations.BuildStep;

public class QuteTemplateAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteTemplateAssetsScannerProcessor.class);

    @BuildStep
    QuteTemplatesBuildItem scan(ProjectResourcesScannerBuildItem scanner,
            WebBundlerConfig config)
            throws IOException {

        LOGGER.debug("Web Bundler scan - html templates: start");
        final List<WebAsset> assets = scanner
                .scan(new ProjectResourcesScannerBuildItem.Scanner("glob:*.html", config.charset()));
        LOGGER.debugf("Web Bundler scan - html templates: %d found.", assets.size());
        return new QuteTemplatesBuildItem(assets);
    }

    private record HtmlTemplatesContext(WebBundlerConfig config, List<WebAsset> assets) {
    }
}
