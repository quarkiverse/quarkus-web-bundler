package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.common.runtime.Responsive;
import io.quarkiverse.web.bundler.common.runtime.ResponsiveSectionHelperFactory;
import io.quarkiverse.web.bundler.deployment.items.QuteRuntimeTemplateBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.runtime.ResponsiveRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.ApplicationArchivesBuildItem;
import io.quarkus.qute.Expression;
import io.quarkus.qute.SectionNode;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateNode;

public class ResponsiveAssetsProcessor {

    private static final Logger LOGGER = Logger.getLogger(ResponsiveAssetsProcessor.class);

    // From https://dev.to/razbakov/responsive-images-best-practices-in-2025-4dlb
    private static final int[] DIMENSIONS = new int[] {
            640,
            1024,
            1920,
            2560,
    };

    @BuildStep
    void initBundleBean(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(Responsive.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ResponsiveSectionHelperFactory.class));
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void scanRuntimeQuteTemplates(ResponsiveRecorder responsiveRecorder,
            BeanContainerBuildItem beanContainer,
            List<QuteRuntimeTemplateBuildItem> quteRuntimeTemplateBuildItem,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            WebBundlerTargetDirBuildItem targetDirBuildItem,
            WebBundlerConfig config,
            ApplicationArchivesBuildItem applicationArchives) {
        if (quteRuntimeTemplateBuildItem.isEmpty()) {
            return;
        }
        // this one collects responsives at build time
        Responsive responsive = new Responsive();
        String staticWebPath = config.webRoot();
        Path staticResourcesPath = applicationArchives.getRootArchive().getChildPath(staticWebPath);

        for (QuteRuntimeTemplateBuildItem runtimeTemplateBuildItem : quteRuntimeTemplateBuildItem) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Inspecting template %s for responsive tags", runtimeTemplateBuildItem.templatePath);
            }
            // FIXME: disallow non-absolute resources since we cannot really guess what URI a template will end up in, and
            // we don't want to place images in the templates folder
            runtimeTemplateBuildItem.sectionNodes.stream().map(TemplateNode::asSection)
                    .filter(s -> s.getName().equals("responsive"))
                    .forEach(resp -> collectResponsive(resp, Path.of(runtimeTemplateBuildItem.templatePath), responsive,
                            staticResourceProducer,
                            staticResourcesPath, targetDirBuildItem.dist()));

        }

        // now populate the runtime value from the build-time value
        for (Map.Entry<io.quarkiverse.web.bundler.common.runtime.Responsive.ResponsiveImage, List<String>> entry : responsive
                .collectUsers().entrySet()) {
            responsiveRecorder.addResponsive(beanContainer.getValue(), entry.getKey().id, entry.getKey().fileName,
                    entry.getValue(), entry.getKey().collectVariants());
        }
    }

    public static void scanResponsiveTags(Template template, Responsive responsive, WebAsset webAsset,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer, String pathFromWebRoot, Path targetDist) {
        Path webAssetPath = webAsset.resource().path();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Inspecting template %s for responsive tags", webAssetPath);
        }
        // This is disgusting, but I (Stef) could not find how to do this cleanly
        String webAssetPathString = webAssetPath.toString();
        if (!webAssetPathString.endsWith(pathFromWebRoot)) {
            throw new RuntimeException("Failed to find absolute web root from resource " + webAssetPath
                    + ": does not end with resource path: " + pathFromWebRoot);
        }
        Path webFolderPath = Path.of(webAssetPathString.substring(0, webAssetPathString.length() - pathFromWebRoot.length()));
        template.findNodes(TemplateNode::isSection).stream().map(TemplateNode::asSection)
                .filter(s -> s.getName().equals("responsive"))
                .forEach(resp -> collectResponsive(resp, webAsset.resource().path(), responsive, staticResourceProducer,
                        webFolderPath, targetDist));
    }

    private static void collectResponsive(SectionNode resp, Path templatePath, Responsive responsive,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            Path webFolderPath, Path targetDist) {
        List<Expression> expressions = resp.getExpressions();
        if (expressions.size() == 1) {
            Expression expr = expressions.get(0);
            if (expr.isLiteral()) {
                Object literal = expr.getLiteral();
                if (literal instanceof String file) {
                    Path imagePath = Path.of(file);
                    // We can't use Path.isAbsolute on Windows, and our paths are expected to be URIs anyways
                    Path absoluteImagePath = file.startsWith("/")
                            // we need to resolve by passing a string, otherwise we get an exception due to different FS providers
                            // for zip filesystems
                            ? webFolderPath.resolve(imagePath.subpath(0, imagePath.getNameCount()).toString())
                            : templatePath.getParent().resolve(imagePath);
                    if (!Files.isRegularFile(absoluteImagePath)) {
                        throw new RuntimeException("Image does not exist or is not a file: " + imagePath);
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf(" Found responsive tag for image: %s", imagePath);
                    }
                    Responsive.ResponsiveImage collectedImage = responsive.addImage(absoluteImagePath);
                    responsive.registerImageUser(resp.getOrigin().getTemplateId(), file, collectedImage);
                    processResponsiveImage(absoluteImagePath, collectedImage, staticResourceProducer, targetDist);
                } else {
                    throw new RuntimeException("Invalid responsive literal: " + literal + " (must be a string literal)");
                }
            } else {
                throw new RuntimeException("Invalid responsive parameter: " + expr + " (must be a string literal)");
            }
        } else {
            throw new RuntimeException(
                    "Invalid responsive parameter list: " + expressions + " (must be a single string literal)");
        }
    }

    private static void processResponsiveImage(Path absoluteImagePath,
            Responsive.ResponsiveImage responsiveImage, BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            Path targetDist) {
        try {
            // FIXME: we should only read the image once to figure out its size, and later generate all collected variants
            BufferedImage image = null;
            String format = null;
            // We can't use Path.toFile() which does not work for zip entries, so we can't pass a File to createImageInputStream
            byte[] bytes = Files.readAllBytes(absoluteImagePath);
            try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
                while (imageReaders.hasNext()) {
                    ImageReader imageReader = imageReaders.next();
                    try {
                        imageReader.setInput(imageInputStream);
                        // ignore if this throws: bad format
                        image = imageReader.read(0);
                        format = imageReader.getFormatName();
                        // if this worked, we found the image format
                        break;
                    } finally {
                        imageReader.dispose();
                    }
                }
            }
            int width = image.getWidth();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf(" Image has width: %s and format: %s, scaling to dimensions: %s", width, format,
                        Arrays.toString(DIMENSIONS));
            }
            for (int dimension : DIMENSIONS) {
                if (width > dimension) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  [%s] Need to resize", dimension);
                    }
                    scaleImage(image, absoluteImagePath, format, dimension,
                            responsiveImage, staticResourceProducer, targetDist);
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf("  [%s] No need to resize (source image is smaller)", dimension);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void scaleImage(BufferedImage image, Path absoluteImagePath, String format, int width,
            Responsive.ResponsiveImage responsiveImage, BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            Path targetDist)
            throws IOException {
        responsiveImage.addScaledImage(width, newVariant -> {
            Image scaledImage = image.getScaledInstance(width, -1, Image.SCALE_DEFAULT);
            Path scaledAbsolutePath = resizedImagePath(targetDist, newVariant);
            RenderedImage scaledImageRendered;
            if (scaledImage instanceof RenderedImage s) {
                scaledImageRendered = s;
            } else {
                BufferedImage buffered = new BufferedImage(scaledImage.getWidth(null), scaledImage.getHeight(null),
                        image.getType());
                Graphics graphics = buffered.getGraphics();
                graphics.drawImage(scaledImage, 0, 0, null);
                graphics.dispose();
                scaledImageRendered = buffered;
            }
            // FIXME: perhaps keeps this in memory?
            try {
                ImageIO.write(scaledImageRendered, format, scaledAbsolutePath.toFile());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String scaledWebPath = prefixWithSlash(newVariant.uriPath());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("   [%s] Resized to %s, accessible at: %s", width, scaledAbsolutePath, scaledWebPath);
            }
            staticResourceProducer.produce(new GeneratedWebResourceBuildItem(
                    scaledWebPath,
                    new WebAsset.Resource(scaledAbsolutePath), GeneratedWebResourceBuildItem.SourceType.STATIC_ASSET));
        });
    }

    private static String pathFromWebRoot(String resource, String root) {
        if (!resource.startsWith(root)) {
            throw new IllegalStateException("Web Bundler must be located under the root: " + root);
        }
        return resource.substring(root.endsWith("/") ? root.length() : root.length() + 1);
    }

    private static Path resizedImagePath(Path targetPath,
            Responsive.ResponsiveImage.Variant newVariant) {
        // Make sure the target folder exists
        Path targetResponsivePath = targetPath.resolve(newVariant.path);
        try {
            Files.createDirectories(targetResponsivePath.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return targetResponsivePath;
    }
}
