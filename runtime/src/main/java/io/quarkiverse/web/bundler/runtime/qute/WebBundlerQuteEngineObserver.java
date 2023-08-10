package io.quarkiverse.web.bundler.runtime.qute;

import static io.quarkiverse.web.bundler.runtime.qute.WebBundlerQuteContextRecorder.WEB_BUNDLER_ID_PREFIX;

import java.io.Reader;
import java.io.StringReader;
import java.util.Optional;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.runtime.qute.WebBundlerQuteContextRecorder.WebBundlerQuteContext;
import io.quarkus.qute.EngineBuilder;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.UserTagSectionHelper;
import io.quarkus.qute.Variant;

@Singleton
public class WebBundlerQuteEngineObserver {

    private static final Logger LOGGER = Logger.getLogger(WebBundlerQuteEngineObserver.class);

    private final WebBundlerQuteContext webBundlerQuteContext;

    @Inject
    public WebBundlerQuteEngineObserver(WebBundlerQuteContext context) {
        this.webBundlerQuteContext = context;
    }

    void observeEngineBuilder(@Observes EngineBuilder builder) {
        builder.addLocator(this::locate);
        for (String tag : webBundlerQuteContext.tags()) {
            String tagTemplateId = WEB_BUNDLER_ID_PREFIX + tag;
            LOGGER.debugf("Registered UserTagSectionHelper for %s [%s]", tag, tagTemplateId);
            builder.addSectionHelper(new UserTagSectionHelper.Factory(tag, tagTemplateId));
        }
    }

    private Optional<TemplateLocator.TemplateLocation> locate(String s) {
        if (!s.startsWith(WEB_BUNDLER_ID_PREFIX)
                || !webBundlerQuteContext.templates().containsKey(s)) {
            return Optional.empty();
        }

        return Optional.of(new TemplateLocator.TemplateLocation() {
            @Override
            public Reader read() {
                return new StringReader(webBundlerQuteContext.templates().get(s));
            }

            @Override
            public Optional<Variant> getVariant() {
                return Optional.empty();
            }
        });
    }

}
