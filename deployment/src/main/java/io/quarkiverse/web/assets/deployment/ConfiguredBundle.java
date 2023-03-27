package io.quarkiverse.web.assets.deployment;

import java.util.Objects;
import java.util.Optional;

public class ConfiguredBundle implements WebAssetsConfig.BundleConfig {

    private final String dir;
    private final String key;
    private final String glob;

    public ConfiguredBundle(String dir, String key, String glob) {
        this.dir = dir;
        this.key = key;
        this.glob = glob;
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
    public String glob() {
        return glob;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConfiguredBundle that = (ConfiguredBundle) o;
        return Objects.equals(dir, that.dir) && Objects.equals(key, that.key) && Objects.equals(glob,
                that.glob);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dir, key, glob);
    }
}
