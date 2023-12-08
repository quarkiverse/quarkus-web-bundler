package io.quarkiverse.web.bundler.deployment;

import java.util.Objects;
import java.util.Optional;

public class ConfiguredEntryPoint implements WebBundlerConfig.EntryPointConfig {

    private final String id;
    private final String key;
    private final String dir;

    public ConfiguredEntryPoint(String dir, String id, String key) {
        this.dir = dir;
        this.id = id;
        this.key = key;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public Optional<String> dir() {
        return Optional.of(dir);
    }

    @Override
    public Optional<String> key() {
        return Optional.of(key);
    }

    @Override
    public boolean quteTags() {
        return false;
    }

    public String id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConfiguredEntryPoint that = (ConfiguredEntryPoint) o;
        return Objects.equals(id, that.id) && Objects.equals(key, that.key)
                && Objects.equals(dir, that.dir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key, dir);
    }
}
