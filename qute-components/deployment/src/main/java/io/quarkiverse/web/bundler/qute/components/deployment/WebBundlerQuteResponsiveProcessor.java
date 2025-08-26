package io.quarkiverse.web.bundler.qute.components.deployment;

import io.quarkiverse.web.bundler.deployment.items.QuteRuntimeTemplateBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.qute.TemplateNode;
import io.quarkus.qute.deployment.TemplatesAnalysisBuildItem;

public class WebBundlerQuteResponsiveProcessor {
    @BuildStep
    void scanRuntimeQuteTemplates(
            BuildProducer<QuteRuntimeTemplateBuildItem> quteRuntimeTemplateBuildItemBuildProducer,
            TemplatesAnalysisBuildItem templatesAnalysisBuildItem) {
        // Collect runtime templates that are build-time validated, and pass them on to the normal deployment
        // processor (which doesn't depend on quarkus-qute unlike this module)
        for (TemplatesAnalysisBuildItem.TemplateAnalysis analysis : templatesAnalysisBuildItem.getAnalysis()) {
            quteRuntimeTemplateBuildItemBuildProducer
                    .produce(new QuteRuntimeTemplateBuildItem(analysis.findNodes(TemplateNode::isSection),
                            analysis.path));
        }
    }

}
