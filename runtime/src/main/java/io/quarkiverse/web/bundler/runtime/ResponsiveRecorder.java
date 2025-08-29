package io.quarkiverse.web.bundler.runtime;

import java.util.List;
import java.util.Map;

import io.quarkiverse.web.bundler.common.runtime.Responsive;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ResponsiveRecorder {
    public void addResponsive(BeanContainer beanContainer, String id, String imageFileName,
            List<String> users, Map<Integer, String> variants) {
        Responsive responsive = beanContainer.beanInstance(Responsive.class);
        responsive.restoreImage(id, imageFileName, users).restoreVariants(variants);
    }
}
