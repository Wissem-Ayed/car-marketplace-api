package com.carmarketplace.car.application;

import com.carmarketplace.car.domain.PhotoVariant;
import com.carmarketplace.common.domain.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhotoProcessorTest {

    private final PhotoProcessor processor = new PhotoProcessor(2);

    @Test
    void createsThreeJpegSizesThatFitTheirLimits() {
        ProcessedPhoto photo = processor.process(upload("car.jpg", TestImages.jpeg(3000, 2000)));

        assertThat(dimensions(photo, PhotoVariant.THUMBNAIL)).containsExactly(400, 267);
        assertThat(dimensions(photo, PhotoVariant.MEDIUM)).containsExactly(1024, 683);
        assertThat(dimensions(photo, PhotoVariant.LARGE)).containsExactly(1920, 1280);
        assertThat(photo.width()).isEqualTo(1920);
        assertThat(photo.height()).isEqualTo(1280);
        assertThat(photo.variants().values()).allSatisfy(bytes ->
                assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xFF, 0xD8, 0xFF));
    }

    @Test
    void neverEnlargesSmallPhotos() {
        ProcessedPhoto photo = processor.process(upload("small.jpg", TestImages.jpeg(300, 200)));

        assertThat(PhotoVariant.values()).allSatisfy(variant ->
                assertThat(dimensions(photo, variant)).containsExactly(300, 200));
    }

    @Test
    void removesExifMetadataAfterApplyingTheRotation() {
        byte[] landscapePixelsShotInPortrait = TestImages.withExifOrientation(TestImages.jpeg(400, 200), 6);
        assertThat(TestImages.containsExif(landscapePixelsShotInPortrait)).isTrue();

        ProcessedPhoto photo = processor.process(upload("phone.jpg", landscapePixelsShotInPortrait));

        assertThat(dimensions(photo, PhotoVariant.LARGE)).containsExactly(200, 400);
        assertThat(photo.variants().values()).noneMatch(TestImages::containsExif);
    }

    @Test
    void flattensTransparentPngOnWhite() {
        ProcessedPhoto photo = processor.process(upload("logo.png", TestImages.transparentPng(100, 100)));

        BufferedImage large = TestImages.read(photo.variants().get(PhotoVariant.LARGE));
        Color center = new Color(large.getRGB(50, 50));
        assertThat(center.getRed()).isGreaterThan(245);
        assertThat(center.getGreen()).isGreaterThan(245);
        assertThat(center.getBlue()).isGreaterThan(245);
    }

    @Test
    void readsWebp() {
        ProcessedPhoto photo = processor.process(upload("tiny.webp", TestImages.WEBP_1X1));

        assertThat(dimensions(photo, PhotoVariant.LARGE)).containsExactly(1, 1);
    }

    @Test
    void rejectsFilesThatAreNotImagesWhateverTheirName() {
        assertThatThrownBy(() -> processor.process(upload("notes.jpg", "hello".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Photo 'notes.jpg' is not a supported image; use JPEG, PNG or WebP");
    }

    @Test
    void rejectsHeicWithAHelpfulMessage() {
        assertThatThrownBy(() -> processor.process(upload("IMG_0042.HEIC", TestImages.heicHeader())))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Photo 'IMG_0042.HEIC' is a HEIC/HEIF photo, which is not supported; convert it to JPEG");
    }

    @Test
    void rejectsFilesLargerThanTenMegabytes() {
        byte[] tooBig = new byte[(int) PhotoProcessor.MAX_FILE_SIZE_BYTES + 1];
        tooBig[0] = (byte) 0xFF;
        tooBig[1] = (byte) 0xD8;
        tooBig[2] = (byte) 0xFF;

        assertThatThrownBy(() -> processor.process(upload("huge.jpg", tooBig)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Photo 'huge.jpg' is larger than 10 MB");
    }

    @Test
    void rejectsDecompressionBombsBeforeDecodingThem() {
        byte[] claims48Megapixels = TestImages.pngHeaderOnly(8000, 6000);

        assertThatThrownBy(() -> processor.process(upload("bomb.png", claims48Megapixels)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Photo 'bomb.png' has more than 40 megapixels");
    }

    @Test
    void rejectsCorruptedImages() {
        byte[] corrupted = Arrays.copyOf(TestImages.jpeg(200, 200), 40);

        assertThatThrownBy(() -> processor.process(upload("broken.jpg", corrupted)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Photo 'broken.jpg' could not be read as an image");
    }

    private static PhotoUpload upload(String name, byte[] content) {
        return new PhotoUpload(name, content);
    }

    private static int[] dimensions(ProcessedPhoto photo, PhotoVariant variant) {
        BufferedImage image = TestImages.read(photo.variants().get(variant));
        return new int[]{image.getWidth(), image.getHeight()};
    }
}
