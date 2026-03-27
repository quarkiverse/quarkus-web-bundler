package io.quarkiverse.web.bundler.deployment;

import java.io.IOException;
import java.util.List;

import org.jboss.logging.Logger;

import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.web.bundler.deployment.config.WebBundlerConfig;
import io.quarkiverse.web.bundler.deployment.items.PublicAssetsBuildItem;
import io.quarkus.deployment.annotations.BuildStep;

public class StaticAssetsScannerProcessor {

    private static final Logger LOGGER = Logger.getLogger(StaticAssetsScannerProcessor.class);

    @BuildStep
    PublicAssetsBuildItem scan(ProjectScannerBuildItem scanner,
            WebBundlerConfig config)
            throws IOException {

        LOGGER.debug("Web Bundler scan - public/static assets: start");
        final List<ProjectFile> assets = scanner.query()
                .scopeDirs(
                        config.prefixWithWebRoot("public"),
                        config.prefixWithWebRoot("static"))
                .addExcluded(config.ignoredFilesOrEmpty())
                .list();
        LOGGER.debugf("Web Bundler scan - public/static assets: %d found.", assets.size());

        return new PublicAssetsBuildItem(assets);
    }

}
