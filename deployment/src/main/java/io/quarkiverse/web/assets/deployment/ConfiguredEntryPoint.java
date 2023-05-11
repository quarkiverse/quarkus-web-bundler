package io.quarkiverse.web.assets.deployment;

import java.util.Objects;
import java.util.Optional;

public class ConfiguredEntryPoint implements WebAssetsConfig.EntryPointConfig {

    private final String id;
    private final String entryPointKey;
    private final String dir;

    public ConfiguredEntryPoint(String dir, String id, String entryPointKey) {
        this.dir = dir;
        this.id = id;
        this.entryPointKey = entryPointKey;
    }

    @Override
    public Optional<String> dir() {
        return Optional.of(dir);
    }

    @Override
    public Optional<String> entryPointKey() {
        return Optional.of(entryPointKey);
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
        return Objects.equals(id, that.id) && Objects.equals(entryPointKey, that.entryPointKey)
                && Objects.equals(dir, that.dir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, entryPointKey, dir);
    }
}
