package com.carmarketplace.car.application;

import com.carmarketplace.car.domain.PhotoVariant;
import com.carmarketplace.common.domain.BusinessRuleViolationException;
import com.carmarketplace.common.domain.ServiceBusyException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class PhotoProcessor {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    static final long MAX_PIXELS = 40_000_000L;
    private static final float JPEG_QUALITY = 0.85f;
    private static final Duration MAX_WAIT_FOR_PROCESSING = Duration.ofSeconds(20);
    private static final Duration RETRY_AFTER = Duration.ofSeconds(10);

    private final Semaphore processingSlots;

    public PhotoProcessor(@Value("${app.photos.max-concurrent-processing:4}") int maxConcurrentProcessing) {
        ImageIO.scanForPlugins();
        this.processingSlots = new Semaphore(maxConcurrentProcessing, true);
    }

    public ProcessedPhoto process(PhotoUpload upload) {
        byte[] content = upload.content();
        if (content.length > MAX_FILE_SIZE_BYTES) {
            throw rejected(upload, "is larger than 10 MB");
        }
        ensureSupportedFormat(upload);
        ensureReasonableDimensions(upload);

        acquireProcessingSlot();
        try {
            return decodeAndResize(upload);
        } finally {
            processingSlots.release();
        }
    }

    private void acquireProcessingSlot() {
        try {
            if (!processingSlots.tryAcquire(MAX_WAIT_FOR_PROCESSING.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ServiceBusyException("Too many photos are being processed right now; retry shortly",
                        RETRY_AFTER);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException("Photo processing was interrupted; retry shortly", RETRY_AFTER);
        }
    }

    private static ProcessedPhoto decodeAndResize(PhotoUpload upload) {
        BufferedImage image = flattenOnWhite(readApplyingOrientation(upload));
        Map<PhotoVariant, byte[]> variants = new EnumMap<>(PhotoVariant.class);
        BufferedImage largest = image;
        for (PhotoVariant variant : PhotoVariant.values()) {
            BufferedImage resized = fitWithin(image, variant.maxSize());
            variants.put(variant, encodeJpeg(resized));
            largest = resized;
        }
        return new ProcessedPhoto(largest.getWidth(), largest.getHeight(), variants);
    }

    private static void ensureSupportedFormat(PhotoUpload upload) {
        byte[] bytes = upload.content();
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)
                || startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)
                || (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P'))) {
            return;
        }
        if (startsWith(bytes, 4, 'f', 't', 'y', 'p') && isHeifBrand(bytes)) {
            throw rejected(upload, "is a HEIC/HEIF photo, which is not supported; convert it to JPEG");
        }
        throw rejected(upload, "is not a supported image; use JPEG, PNG or WebP");
    }

    private static void ensureReasonableDimensions(PhotoUpload upload) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload.content()))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw rejected(upload, "could not be read as an image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw rejected(upload, "has more than %d megapixels".formatted(MAX_PIXELS / 1_000_000));
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw rejected(upload, "could not be read as an image");
        }
    }

    private static BufferedImage readApplyingOrientation(PhotoUpload upload) {
        try {
            return Thumbnails.of(new ByteArrayInputStream(upload.content()))
                    .scale(1.0)
                    .useExifOrientation(true)
                    .asBufferedImage();
        } catch (IOException | IllegalArgumentException e) {
            throw rejected(upload, "could not be read as an image");
        }
    }

    private static BufferedImage flattenOnWhite(BufferedImage image) {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static BufferedImage fitWithin(BufferedImage image, int maxSize) {
        int longestSide = Math.max(image.getWidth(), image.getHeight());
        if (longestSide <= maxSize) {
            return image;
        }
        try {
            return Thumbnails.of(image).size(maxSize, maxSize).keepAspectRatio(true).asBufferedImage();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(output)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static boolean isHeifBrand(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }
        String brand = new String(Arrays.copyOfRange(bytes, 8, 12), StandardCharsets.US_ASCII);
        return brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1");
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessRuleViolationException rejected(PhotoUpload upload, String reason) {
        return new BusinessRuleViolationException("Photo '%s' %s".formatted(upload.fileName(), reason));
    }
}
