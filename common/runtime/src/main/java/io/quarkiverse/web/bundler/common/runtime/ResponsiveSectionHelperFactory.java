package io.quarkiverse.web.bundler.common.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import jakarta.inject.Inject;

import io.quarkus.qute.EngineConfiguration;
import io.quarkus.qute.Expression;
import io.quarkus.qute.ResultNode;
import io.quarkus.qute.Scope;
import io.quarkus.qute.SectionHelper;
import io.quarkus.qute.SectionHelperFactory;
import io.quarkus.qute.TemplateNode;
import io.quarkus.qute.TextNode;

@EngineConfiguration
public class ResponsiveSectionHelperFactory implements SectionHelperFactory<SectionHelper> {

    @Inject
    Responsive responsive;

    // Used by CDI for runtime and build-time validation (of runtime templates)
    public ResponsiveSectionHelperFactory() {
        responsive = null;
    }

    // Used for build-time templates
    public ResponsiveSectionHelperFactory(Responsive responsive) {
        this.responsive = responsive;
    }

    @Override
    public List<String> getDefaultAliases() {
        return Arrays.asList("responsive");
    }

    @Override
    public ParametersInfo getParameters() {
        return ParametersInfo.builder().addParameter("it").build();
    }

    @Override
    public Scope initializeBlock(Scope outerScope, BlockInfo block) {
        if (!block.getLabel().equals("$main")) {
            return outerScope;
        } else {
            for (Map.Entry<String, String> entry : block.getParameters().entrySet()) {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                block.addExpression(key, value);
            }

            return outerScope;
        }
    }

    @Override
    public SectionHelper initialize(SectionInitContext context) {
        Expression fileExpression = context.getExpression("it");
        TemplateNode.Origin origin = context.getOrigin();
        return new SectionHelper() {
            @Override
            public CompletionStage<ResultNode> resolve(SectionResolutionContext context) {
                return context.evaluate(fileExpression)
                        .thenApply(fileObject -> {
                            Responsive.ResponsiveImage responsiveImage = responsive.get(
                                    context.resolutionContext().getTemplate().getId(), (String) fileObject);
                            return new TextNode(
                                    "<img src=\"" + fileObject + "\" srcset=\"" + responsiveImage.srcset()
                                            + "\"/>",
                                    origin);
                        });
            }
        };
    }
}
