package io.quarkiverse.web.bundler.deployment.items;

import java.util.Collection;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.qute.TemplateNode;

/**
 * Represents a list of Qute templates to scan for image tags
 */
public final class QuteRuntimeTemplateBuildItem extends MultiBuildItem {

    public final Collection<TemplateNode> sectionNodes;
    public final String templatePath;

    public QuteRuntimeTemplateBuildItem(Collection<TemplateNode> sectionNodes, String templatePath) {
        this.sectionNodes = sectionNodes;
        this.templatePath = templatePath;
    }
}
