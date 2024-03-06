package io.quarkiverse.web.bundler.qute.components.deployment;

import io.quarkiverse.web.bundler.deployment.WebBundlerConfig;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceDirectoryBuildItem;
import io.quarkus.qute.deployment.TemplateRootBuildItem;

class WebBundlerQuteTemplateRootProcessor {

    @BuildStep
    void provideWebTemplateRoot(
            BuildProducer<NativeImageResourceDirectoryBuildItem> nativeImageResourceDirectoryProducer,
            BuildProducer<TemplateRootBuildItem> templateRootProducer,
            WebBundlerConfig config) {
        templateRootProducer.produce(new TemplateRootBuildItem(config.fromWebRoot("templates")));
    }

}
