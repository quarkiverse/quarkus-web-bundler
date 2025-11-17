package io.quarkiverse.web.bundler.runtime.devmode;

import java.util.List;

public class WebBundlingException extends RuntimeException {

    private final List<String> errors;

    public WebBundlingException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public List<String> errors() {
        return errors;
    }
}
