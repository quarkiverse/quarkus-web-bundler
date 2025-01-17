package io.quarkiverse.web.bundler.runtime.devmode;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.runtime.WebBundlerResourceRecorder;
import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;

public class WebBundlerHotReplacementSetup implements HotReplacementSetup {

    private static final Logger LOG = Logger.getLogger(WebBundlerHotReplacementSetup.class);

    private final List<Consumer<Set<String>>> changeEventListeners = new CopyOnWriteArrayList<>();

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        context.consumeNoRestartChanges(this::noRestartChanges);
        WebBundlerResourceRecorder.setHotDeploymentEventHandlerRegister((r) -> {
            changeEventListeners.add(r);
            return () -> changeEventListeners.remove(r);
        });
    }

    private void noRestartChanges(Set<String> strings) {
        for (Consumer<Set<String>> changeEventListener : changeEventListeners) {
            changeEventListener.accept(strings);
        }
    }
}
