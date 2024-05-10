package io.quarkiverse.web.bundler.deployment.web;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.builder.item.SimpleBuildItem;

public final class ChangeEventBuildItem extends SimpleBuildItem {

    private static final List<String> IGNORED_SUFFIX = List.of(".map");

    private final List<String> added;
    private final List<String> removed;
    private final List<String> updated;

    public ChangeEventBuildItem(List<String> added, List<String> removed, List<String> updated) {
        this.added = added;
        this.removed = removed;
        this.updated = updated;
    }

    public List<String> added() {
        return added;
    }

    public List<String> removed() {
        return removed;
    }

    public List<String> updated() {
        return updated;
    }

    public static ChangeEventBuildItemBuilder builder() {
        return new ChangeEventBuildItemBuilder();
    }

    public static class ChangeEventBuildItemBuilder {
        private final List<String> added = new ArrayList<>();
        private final List<String> removed = new ArrayList<>();
        private final List<String> updated = new ArrayList<>();

        private ChangeEventBuildItemBuilder() {
        }

        public ChangeEventBuildItemBuilder addAdded(String item) {
            if (matches(IGNORED_SUFFIX, item)) {
                return this;
            }
            this.added.add(item);
            return this;
        }

        public ChangeEventBuildItemBuilder addRemoved(String item) {
            if (matches(IGNORED_SUFFIX, item)) {
                return this;
            }
            this.removed.add(item);
            return this;
        }

        public ChangeEventBuildItemBuilder addUpdated(String item) {
            if (matches(IGNORED_SUFFIX, item)) {
                return this;
            }
            this.updated.add(item);
            return this;
        }

        public ChangeEventBuildItem build() {
            return new ChangeEventBuildItem(added, removed, updated);
        }
    }

    static boolean matches(List<String> suffixes, String name) {
        for (String suffix : suffixes) {
            if (name.toLowerCase().endsWith(suffix.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

}
