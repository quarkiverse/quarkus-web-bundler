package io.quarkiverse.web.bundler.runtime.devmode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.runtime.WebBundlerResourceRecorder;
import io.quarkus.dev.ErrorPageGenerators;
import io.quarkus.dev.spi.HotReplacementContext;
import io.quarkus.dev.spi.HotReplacementSetup;
import io.quarkus.runtime.TemplateHtmlBuilder;

public class WebBundlerHotReplacementSetup implements HotReplacementSetup {

    private static final Logger LOG = Logger.getLogger(WebBundlerHotReplacementSetup.class);

    private final List<Consumer<Set<String>>> changeEventListeners = new CopyOnWriteArrayList<>();

    private static final String WEB_BUNDLING_EXCEPTION = WebBundlingException.class.getName();

    @Override
    public void setupHotDeployment(HotReplacementContext context) {
        context.consumeNoRestartChanges(this::noRestartChanges);
        WebBundlerResourceRecorder.setHotDeploymentEventHandlerRegister((r) -> {
            changeEventListeners.add(r);
            return () -> changeEventListeners.remove(r);
        });
        ErrorPageGenerators.register(WEB_BUNDLING_EXCEPTION, this::generatePage);
    }

    private String generatePage(Throwable t) {
        try {
            Method errorsMethod = t.getClass().getMethod("errors");
            @SuppressWarnings("unchecked")
            List<String> errors = (List<String>) errorsMethod.invoke(t);
            final String subTitle = "Found " + errors.size() + " bundling problems";
            return new TemplateHtmlBuilder("Quarkus Web Bundler Error", subTitle, subTitle)
                    .append("<p><b>The source file for the bundling is copied from the web directory:</b></p>")
                    .append("<pre>" + ansiToHtml(String.join("\n", errors)) + "</pre>")
                    .toString();
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    private void noRestartChanges(Set<String> strings) {
        for (Consumer<Set<String>> changeEventListener : changeEventListeners) {
            changeEventListener.accept(strings);
        }
    }

    public static String ansiToHtml(String text) {
        Pattern p = Pattern.compile("\u001B\\[([0-9;]+)m");
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String[] codes = m.group(1).split(";");
            StringBuilder style = new StringBuilder();
            for (String c : codes) {
                switch (c) {
                    case "0" -> style.append("</span>");
                    case "1" -> style.append("<span style='font-weight:bold'>");
                    case "4" -> style.append("<span style='text-decoration:underline'>");
                    case "30", "37", "97" -> style.append("<span style='color:black'>");
                    case "31" -> style.append("<span style='color:red'>");
                    case "32" -> style.append("<span style='color:green'>");
                    case "33" -> style.append("<span style='color:yellow'>");
                    case "34" -> style.append("<span style='color:blue'>");
                    case "35" -> style.append("<span style='color:magenta'>");
                    case "36" -> style.append("<span style='color:cyan'>");
                    case "90" -> style.append("<span style='color:gray'>");
                }
            }
            m.appendReplacement(sb, style.toString());
        }
        m.appendTail(sb);
        return sb.toString().replace("\r\n", "<br>").replace("\n", "<br>");
    }

}
