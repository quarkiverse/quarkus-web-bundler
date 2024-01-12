package io.quarkiverse.web.bundler.qute.components.deployment;

import static io.quarkiverse.web.bundler.qute.components.runtime.WebBundlerQuteContextRecorder.WEB_BUNDLER_ID_PREFIX;
import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;

import java.util.*;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.deployment.items.QuteTagsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.qute.components.runtime.WebBundlerQuteContextRecorder;
import io.quarkiverse.web.bundler.qute.components.runtime.WebBundlerQuteEngineObserver;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Record;

class WebBundlerQuteComponentsProcessor {

    private static final Logger LOGGER = Logger.getLogger(WebBundlerQuteComponentsProcessor.class);

    @BuildStep
    @Record(STATIC_INIT)
    void initQuteTags(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeans,
            WebBundlerQuteContextRecorder recorder,
            Optional<QuteTagsBuildItem> quteTags) {
        if (quteTags.isEmpty()) {
            return;
        }
        final Map<String, String> templates = new HashMap<>();
        final List<String> tags = new ArrayList<>();
        for (WebAsset webAsset : quteTags.get().getWebAssets()) {
            final String tag = webAsset.filePath().get().getFileName().toString();
            final String tagName = tag.contains(".") ? tag.substring(0, tag.indexOf('.')) : tag;
            templates.put(WEB_BUNDLER_ID_PREFIX + tagName, new String(webAsset.readContentFromFile(), webAsset.charset()));
            tags.add(tagName);
        }
        additionalBeans.produce(new AdditionalBeanBuildItem(WebBundlerQuteEngineObserver.class));
        syntheticBeans.produce(SyntheticBeanBuildItem.configure(WebBundlerQuteContextRecorder.WebBundlerQuteContext.class)
                .supplier(recorder.createContext(tags, templates))
                .done());
    }

}
