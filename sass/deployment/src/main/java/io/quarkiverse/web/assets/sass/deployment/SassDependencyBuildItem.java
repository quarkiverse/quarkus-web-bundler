package io.quarkiverse.web.assets.sass.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class SassDependencyBuildItem extends MultiBuildItem {

    public final String source;
    public final String affectedFile;

    public SassDependencyBuildItem(String source, String affectedFile) {
        this.source = source;
        this.affectedFile = affectedFile;
    }
}
