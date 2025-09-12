package io.quarkiverse.web.bundler.qute.components.deployment;

import java.util.List;

import io.quarkiverse.web.bundler.deployment.items.QuteRuntimeTemplateBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.qute.TemplateNode;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.TemplatesAnalysisBuildItem;

public class WebBundlerQuteImageProcessor {
    @BuildStep
    void scanRuntimeQuteTemplates(
            BuildProducer<QuteRuntimeTemplateBuildItem> quteRuntimeTemplateBuildItemBuildProducer,
            TemplatesAnalysisBuildItem templatesAnalysisBuildItem,
            List<TemplatePathBuildItem> tp) {
        // Collect runtime templates that are build-time validated, and pass them on to the normal deployment
        // processor (which doesn't depend on quarkus-qute unlike this module)
        for (TemplatesAnalysisBuildItem.TemplateAnalysis analysis : templatesAnalysisBuildItem.getAnalysis()) {
            // this is an alternate method to find template sources, but it does not work for Roq which does
            // not register the real source paths (for various reasons)
            //            findTemplatePath(analysis.path, tp);
            // Note that the template ID is not set at this stage, for some reason
            quteRuntimeTemplateBuildItemBuildProducer
                    .produce(new QuteRuntimeTemplateBuildItem(analysis.findNodes(TemplateNode::isSection),
                            analysis.path));
        }
    }

    // Kept in case we need it later
    //    private void findTemplatePath(String path, List<TemplatePathBuildItem> tp) {
    //        System.err.println("Looking for template " + path);
    //        for (TemplatePathBuildItem templatePathBuildItem : tp) {
    //            //            System.err.println(" Looking at " + templatePathBuildItem.getPath());
    //            if (path.equals(templatePathBuildItem.getPath())) {
    //                System.err.println("  Full path: " + templatePathBuildItem.getFullPath());
    //                return;
    //            }
    //        }
    //        System.err.println(" Not Found");
    //    }

}
