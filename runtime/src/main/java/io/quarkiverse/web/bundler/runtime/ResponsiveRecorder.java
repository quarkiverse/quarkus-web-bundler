package io.quarkiverse.web.bundler.runtime;

import java.util.Map;

import io.quarkiverse.web.bundler.common.runtime.Responsive;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class ResponsiveRecorder {
    public RuntimeValue<Responsive.ResponsiveImage> addResponsive(BeanContainer beanContainer, String id, String imageFileName,
            Map<Integer, String> variants) {
        Responsive responsive = beanContainer.beanInstance(Responsive.class);
        Responsive.ResponsiveImage image = responsive.restoreImage(id, imageFileName);
        image.restoreVariants(variants);
        return new RuntimeValue<>(image);
    }

    public void addImageUser(BeanContainer beanContainer, String templateId, String declaredURI, String runtimeURI,
            RuntimeValue<Responsive.ResponsiveImage> image) {
        Responsive responsive = beanContainer.beanInstance(Responsive.class);
        responsive.registerImageUser(templateId, declaredURI, runtimeURI, image.getValue());
    }
}
