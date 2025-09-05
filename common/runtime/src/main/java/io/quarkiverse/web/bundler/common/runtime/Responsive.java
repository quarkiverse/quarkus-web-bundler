package io.quarkiverse.web.bundler.common.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("responsive")
public class Responsive {

    // cache of absolute image path to image id
    Map<Path, String> imageIdsByPath = new HashMap<>();
    // map of image (id/file) to image
    Map<String, ResponsiveImage> images = new HashMap<>();
    // map of user (by key:templateid/file) to image user
    Map<String, ResponsiveImageUser> users = new HashMap<>();

    public ResponsiveImage addImage(Path absoluteImagePath, String targetFileName) {
        String id = imageIdsByPath.get(absoluteImagePath);
        if (id == null) {
            id = digest(absoluteImagePath);
            imageIdsByPath.put(absoluteImagePath, id);
        }
        /*
         * The idea here is that we want to make sure responsives for a unique absolute path end up in the same folder (same id,
         * same file name),
         * and if someone has the same file (same id) in more than one absolute path, they also end up in the same folder (same
         * id, same file name),
         * and if someone has the same file (same id) in more than one absolute path under a different file name, they also end
         * up in the same
         * folder (same id), but with different file names.
         */
        String finalId = id;
        String key = keyImage(id, targetFileName);
        return images.computeIfAbsent(key, key2 -> new ResponsiveImage(finalId, targetFileName));
    }

    private String keyImage(String id, String targetFileName) {
        return id + "|" + targetFileName;
    }

    private String digest(Path absoluteImagePath) {
        try (var is = new DigestInputStream(Files.newInputStream(absoluteImagePath), MessageDigest.getInstance("SHA-1"))) {
            byte[] buffer = new byte[8192];
            while (is.read(buffer) > -1) {
            }
            byte[] digest = is.getMessageDigest().digest();
            // only keep the first 8 chars (4 bytes)
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 4; ++i) {
                sb.append(Integer.toHexString(digest[i] & 255 | 256).substring(1, 3));
            }
            return sb.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // for static init
    public ResponsiveImage restoreImage(String id, String imageFileName) {
        ResponsiveImage image = new ResponsiveImage(id, imageFileName);
        images.put(keyImage(id, imageFileName), image);
        return image;
    }

    public Map<ResponsiveImage, Set<ResponsiveImageUser>> collectImageUsers() {
        Map<ResponsiveImage, Set<ResponsiveImageUser>> ret = new HashMap<>();
        for (var user : users.values()) {
            ret.computeIfAbsent(user.image, x -> new HashSet<>()).add(user);
        }
        return ret;
    }

    public void registerImageUser(String templateId, String declaredUri, String runtimeUri, ResponsiveImage responsiveImage) {
        users.put(keyImageUser(templateId, declaredUri),
                new ResponsiveImageUser(templateId, declaredUri, runtimeUri, responsiveImage));
    }

    public ResponsiveImageUser get(String templateId, String declaredURI) {
        return users.get(keyImageUser(templateId, declaredURI));
    }

    private String keyImageUser(String templateId, String declaredURI) {
        return templateId + "|" + declaredURI;
    }

    /**
     * This represents a responsive tag, pointing to a responsive image
     */
    public static class ResponsiveImageUser {
        public final ResponsiveImage image;
        public final String runtimeURI;
        public final String declaredURI;
        public final String templateId;
        // Eventually, this will list the variants in use

        public ResponsiveImageUser(String templateId, String declaredURI, String runtimeURI, ResponsiveImage image) {
            this.templateId = templateId;
            this.declaredURI = declaredURI;
            this.image = image;
            this.runtimeURI = runtimeURI;
        }
    }

    /**
     * This represents a unique responsive image for a unique path along with all its generated variants
     */
    public static class ResponsiveImage {

        public final TreeMap<Integer, Variant> variants = new TreeMap<>();
        public final String id;
        public final String fileName;

        public ResponsiveImage(String id, String imageFileName) {
            this.id = id;
            this.fileName = imageFileName;
        }

        // for static init
        public void restoreVariants(Map<Integer, String> variants) {
            for (Map.Entry<Integer, String> entry : variants.entrySet()) {
                this.variants.put(entry.getKey(), new Variant(entry.getKey(), Path.of(entry.getValue())));
            }
        }

        public Map<Integer, String> collectVariants() {
            Map<Integer, String> ret = new HashMap<>();
            for (Map.Entry<Integer, Variant> entry : variants.entrySet()) {
                ret.put(entry.getKey(), entry.getValue().path.toString());
            }
            return ret;
        }

        public void addScaledImage(int width, Consumer<Variant> consumer) {
            variants.computeIfAbsent(width, key -> {
                Variant newVariant = new Variant(width);
                consumer.accept(newVariant);
                return newVariant;
            });
        }

        public String srcset() {
            StringBuilder sb = new StringBuilder();
            for (Variant variant : variants.values()) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append("/").append(variant.uriPath()).append(" ").append(variant.width).append("w");
            }

            return sb.toString();
        }

        public class Variant {

            public final int width;
            // this is relative to the target dir: responsives/id/filename-width.ext
            public final Path path;

            public Variant(int width) {
                this.width = width;
                this.path = computePath();
            }

            Variant(int width, Path path) {
                this.width = width;
                this.path = path;
            }

            private Path computePath() {
                int lastDot = fileName.lastIndexOf('.');
                String newFileName;
                if (lastDot != -1) {
                    newFileName = fileName.substring(0, lastDot) + "_" + width + fileName.substring(lastDot);
                } else {
                    newFileName = fileName + "_" + width;
                }
                return Path.of("responsives", id, newFileName);
            }

            public String uriPath() {
                // make sure this works on Windows too
                return path.toString().replace('\\', '/');
            }
        }
    }
}
