package io.quarkiverse.web.bundler.runtime.qute;

import java.io.Reader;
import java.io.StringReader;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jboss.logging.Logger;

import io.quarkus.qute.EngineBuilder;
import io.quarkus.qute.EvalContext;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.UserTagSectionHelper;
import io.quarkus.qute.ValueResolver;
import io.quarkus.qute.Variant;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

@Singleton
public class WebAssetsQuteEngineObserver {

    private static final Logger LOGGER = Logger.getLogger(WebAssetsQuteEngineObserver.class);

    private final WebAssetsQuteContextRecorder.WebAssetsQuteContext webAssetsQuteContext;

    public WebAssetsQuteEngineObserver(WebAssetsQuteContextRecorder.WebAssetsQuteContext context) {
        this.webAssetsQuteContext = context;
    }

    void observeEngineBuilder(@Observes EngineBuilder builder) {
        builder.addLocator(this::locate);
        builder.addValueResolver(new ValueResolver() {

            public boolean appliesTo(EvalContext context) {
                return context.getName().equals("bundle");
            }

            @Override
            public CompletionStage<Object> resolve(EvalContext context) {
                return CompletableFuture.completedFuture(webAssetsQuteContext.bundle());
            }
        });
        for (String tag : webAssetsQuteContext.tags()) {
            String tagTemplateId = WebAssetsQuteContextRecorder.WEB_ASSETS_ID_PREFIX + tag;
            LOGGER.debugf("Registered UserTagSectionHelper for %s [%s]", tag, tagTemplateId);
            builder.addSectionHelper(new UserTagSectionHelper.Factory(tag, tagTemplateId));
        }
    }

    private Optional<TemplateLocator.TemplateLocation> locate(String s) {
        if (!s.startsWith(WebAssetsQuteContextRecorder.WEB_ASSETS_ID_PREFIX)
                || !webAssetsQuteContext.templates().containsKey(s)) {
            return Optional.empty();
        }

        return Optional.of(new TemplateLocator.TemplateLocation() {
            @Override
            public Reader read() {
                return new StringReader(webAssetsQuteContext.templates().get(s));
            }

            @Override
            public Optional<Variant> getVariant() {
                return Optional.empty();
            }
        });
    }

}
