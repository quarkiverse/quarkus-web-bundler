package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.QuteTemplatesBuildItem;
import io.quarkus.deployment.annotations.BuildStep;

public class QuteTemplateAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(QuteTemplateAssetsScannerProcessor.class);

    @BuildStep
    QuteTemplatesBuildItem scan(ProjectScannerBuildItem scanner,
            WebBundlerConfig config)
            throws IOException {

        LOGGER.debug("Web Bundler scan - html templates: start");
        final List<ProjectFile> assets = scanner.query()
                .scopeDirs(config.webRoot())
                .matchingGlob("*.html")
                .addExcluded(config.ignoredFilesOrEmpty())
                .list();
        LOGGER.debugf("Web Bundler scan - html templates: %d found.", assets.size());
        return new QuteTemplatesBuildItem(assets);
    }

}
