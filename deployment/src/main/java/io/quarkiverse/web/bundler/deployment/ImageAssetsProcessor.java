package io.quarkiverse.web.bundler.deployment;

import static io.quarkiverse.web.bundler.deployment.util.PathUtils.prefixWithSlash;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.jboss.logging.Logger;

import io.quarkiverse.web.bundler.common.runtime.ImageSectionHelperFactory;
import io.quarkiverse.web.bundler.common.runtime.Images;
import io.quarkiverse.web.bundler.deployment.items.ImagePathMapperBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ImageSourcePathBuildItem;
import io.quarkiverse.web.bundler.deployment.items.ImagesBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteRuntimeTemplateBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteTemplateSourcePathBuildItem;
import io.quarkiverse.web.bundler.deployment.items.QuteTemplateSourcePathsBuildItem;
import io.quarkiverse.web.bundler.deployment.items.WebAsset;
import io.quarkiverse.web.bundler.deployment.items.WebBundlerTargetDirBuildItem;
import io.quarkiverse.web.bundler.deployment.web.GeneratedWebResourceBuildItem;
import io.quarkiverse.web.bundler.runtime.ImageRecorder;
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
import io.quarkus.runtime.RuntimeValue;

public class ImageAssetsProcessor {

    private static final Logger LOGGER = Logger.getLogger(ImageAssetsProcessor.class);

    // From https://dev.to/razbakov/responsive-images-best-practices-in-2025-4dlb
    private static final int[] DIMENSIONS = new int[] {
            640,
            1024,
            1920,
            2560,
    };

    @BuildStep
    QuteTemplateSourcePathsBuildItem collectTemplatePaths(List<QuteTemplateSourcePathBuildItem> paths) {
        return new QuteTemplateSourcePathsBuildItem(paths);
    }

    @BuildStep
    void initBundleBean(
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(new AdditionalBeanBuildItem(Images.class));
        additionalBeans.produce(new AdditionalBeanBuildItem(ImageSectionHelperFactory.class));
    }

    @Record(ExecutionTime.RUNTIME_INIT)
    @BuildStep
    void scanRuntimeQuteTemplates(ImageRecorder imageRecorder,
            BeanContainerBuildItem beanContainer,
            List<QuteRuntimeTemplateBuildItem> quteRuntimeTemplateBuildItem,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            WebBundlerTargetDirBuildItem targetDirBuildItem,
            WebBundlerConfig config,
            ApplicationArchivesBuildItem applicationArchives,
            QuteTemplateSourcePathsBuildItem quteTemplatePathsBuildItem,
            ImagesBuildItem imagesBuildItem,
            Optional<ImagePathMapperBuildItem> imagePathMapperBuildItem,
            List<ImageSourcePathBuildItem> imageSourcePathBuildItems) {
        if (quteRuntimeTemplateBuildItem.isEmpty()) {
            return;
        }
        // collect image usages, starting from the build-time templates
        Images images = imagesBuildItem.images;
        String staticWebPath = config.webRoot();
        Path staticResourcesPath = applicationArchives.getRootArchive().getChildPath(staticWebPath);

        for (QuteRuntimeTemplateBuildItem runtimeTemplateBuildItem : quteRuntimeTemplateBuildItem) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Inspecting (run-time) template %s for image tags",
                        runtimeTemplateBuildItem.templatePath);
            }
            // default to null
            Path templatePath = quteTemplatePathsBuildItem.templatePathsToSourcePaths
                    .get(runtimeTemplateBuildItem.templatePath);
            // we don't want to place images in the templates folder
            runtimeTemplateBuildItem.sectionNodes.stream().map(TemplateNode::asSection)
                    .filter(s -> s.getName().equals("image"))
                    .forEach(resp -> collectImage(resp,
                            runtimeTemplateBuildItem.templatePath, templatePath,
                            images,
                            staticResourceProducer,
                            staticResourcesPath, targetDirBuildItem.dist(),
                            imagePathMapperBuildItem.orElse(null),
                            imageSourcePathBuildItems));

        }

        // now populate the runtime value from the build-time value
        for (var userEntry : images.collectImageUsers().entrySet()) {
            Images.Image image = userEntry.getKey();
            RuntimeValue<Images.Image> runtimeImage = imageRecorder.addImage(beanContainer.getValue(),
                    image.id, image.fileName,
                    image.collectVariants());
            for (Images.ImageUser user : userEntry.getValue()) {
                imageRecorder.addImageUser(beanContainer.getValue(), user.templateId, user.declaredURI, user.runtimeURI,
                        runtimeImage);
            }
        }
    }

    public static void scanImageTags(Template template, Images images, WebAsset webAsset,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer, String pathFromWebRoot, Path targetDist,
            Optional<ImagePathMapperBuildItem> imagePathMapperBuildItem,
            List<ImageSourcePathBuildItem> imageSourcePathBuildItems) {
        Path webAssetPath = webAsset.resource().path();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debugf("Inspecting (build-time) template %s for image tags", webAssetPath);
        }
        // This is disgusting, but I (Stef) could not find how to do this cleanly
        String webAssetPathString = webAssetPath.toString();
        if (!webAssetPathString.endsWith(pathFromWebRoot)) {
            throw new RuntimeException("Failed to find absolute web root from resource " + webAssetPath
                    + ": does not end with resource path: " + pathFromWebRoot);
        }
        Path webFolderPath = Path.of(webAssetPathString.substring(0, webAssetPathString.length() - pathFromWebRoot.length()));
        template.findNodes(TemplateNode::isSection).stream().map(TemplateNode::asSection)
                .filter(s -> s.getName().equals("image"))
                .forEach(resp -> collectImage(resp, webAsset.resourceName(), webAsset.resource().path(), images,
                        staticResourceProducer,
                        webFolderPath, targetDist, imagePathMapperBuildItem.orElse(null),
                        imageSourcePathBuildItems));
    }

    private static void collectImage(SectionNode resp, String templateName, Path templatePath, Images images,
            BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            Path webFolderPath, Path targetDist, ImagePathMapperBuildItem imagePathMapperBuildItem,
            List<ImageSourcePathBuildItem> imageSourcePathBuildItems) {
        List<Expression> expressions = resp.getExpressions();
        if (expressions.size() == 1) {
            Expression expr = expressions.get(0);
            if (expr.isLiteral()) {
                Object literal = expr.getLiteral();
                if (literal instanceof String file) {
                    Path imagePath = Path.of(file);
                    Images.ResolvedSourceImage resolvedImage;
                    // We can't use Path.isAbsolute on Windows, and our paths are expected to be URIs anyways
                    if (file.startsWith("/")) {
                        // we need to resolve by passing a string, otherwise we get an exception due to different FS providers
                        // for zip filesystems
                        resolvedImage = resolveAbsoluteFile(webFolderPath, imageSourcePathBuildItems,
                                imagePath.subpath(0, imagePath.getNameCount()).toString());
                    } else if (templatePath != null) {
                        Path resolvedPath = templatePath.getParent().resolve(imagePath);
                        resolvedImage = resolveImage(resolvedPath);
                        if (resolvedImage == null) {
                            throw new RuntimeException("Image does not exist or is not a file: " + imagePath + " (looked up at "
                                    + resolvedPath + ")");
                        }
                    } else {
                        throw new RuntimeException(
                                "Cannot refer to relative files from template when we do not know the template path: "
                                        + templateName);
                    }
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debugf(" Found image tag for image: %s", imagePath);
                    }
                    String targetFileName = imagePath.getFileName().toString();
                    if (imagePathMapperBuildItem != null) {
                        targetFileName = imagePathMapperBuildItem
                                .getRuntimeURI(targetFileName);
                    }
                    Images.Image collectedImage = images.addImage(resolvedImage, targetFileName);
                    String runtimeFile = imagePathMapperBuildItem != null
                            ? imagePathMapperBuildItem.getRuntimeURI(file)
                            : file;
                    images.registerImageUser(resp.getOrigin().getTemplateId(), file, runtimeFile, collectedImage);
                    processImage(resolvedImage, collectedImage, staticResourceProducer, targetDist);
                } else {
                    throw new RuntimeException("Invalid image literal: " + literal + " (must be a string literal)");
                }
            } else {
                throw new RuntimeException("Invalid image parameter: " + expr + " (must be a string literal)");
            }
        } else {
            throw new RuntimeException(
                    "Invalid image parameter list: " + expressions + " (must be a single string literal)");
        }
    }

    private static Images.ResolvedSourceImage resolveAbsoluteFile(Path webFolderPath,
            List<ImageSourcePathBuildItem> imageSourcePathBuildItems, String pathToResolve) {
        // It's possible for the web folder path to be null (in Roq)
        if (webFolderPath != null) {
            Path resolvedPath = webFolderPath.resolve(pathToResolve);
            Images.ResolvedSourceImage ret = resolveImage(resolvedPath);
            if (ret != null) {
                return ret;
            }
        }
        for (ImageSourcePathBuildItem imageSourcePathBuildItem : imageSourcePathBuildItems) {
            Path resolvedPath = imageSourcePathBuildItem.path.resolve(pathToResolve);
            Images.ResolvedSourceImage ret = resolveImage(resolvedPath);
            if (ret != null) {
                return ret;
            }
        }
        throw new RuntimeException("Image does not exist or is not a file: " + pathToResolve + " (looked up at "
                + webFolderPath + " and " + imageSourcePathBuildItems.stream().map(bi -> bi.path).toList() + ")");
    }

    private static Images.ResolvedSourceImage resolveImage(Path resolvedPath) {
        try (InputStream resourceStream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resolvedPath.toString())) {
            if (resourceStream != null) {
                return new Images.ResolvedSourceImage(resolvedPath, resourceStream.readAllBytes());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read image " + resolvedPath + " from the classpath", e);
        }
        if (Files.isRegularFile(resolvedPath)) {
            try {
                return new Images.ResolvedSourceImage(resolvedPath, Files.readAllBytes(resolvedPath));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read image " + resolvedPath + " from the filesystem", e);
            }
        }
        return null;
    }

    private static void processImage(Images.ResolvedSourceImage resolvedImage,
            Images.Image processedImage, BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            Path targetDist) {
        try {
            // FIXME: we should only read the image once to figure out its size, and later generate all collected variants
            BufferedImage image = null;
            String format = null;
            try (ImageInputStream imageInputStream = ImageIO
                    .createImageInputStream(new ByteArrayInputStream(resolvedImage.contents()))) {
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
                    scaleImage(image, format, dimension,
                            processedImage, staticResourceProducer, targetDist);
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

    private static void scaleImage(BufferedImage image, String format, int width,
            Images.Image processedImage, BuildProducer<GeneratedWebResourceBuildItem> staticResourceProducer,
            Path targetDist)
            throws IOException {
        processedImage.addScaledImage(width, newVariant -> {
            Image scaledImage = image.getScaledInstance(width, -1, java.awt.Image.SCALE_DEFAULT);
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
            Images.Image.Variant newVariant) {
        // Make sure the target folder exists
        Path targetProcessedImagesPath = targetPath.resolve(newVariant.path);
        try {
            Files.createDirectories(targetProcessedImagesPath.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return targetProcessedImagesPath;
    }
}
