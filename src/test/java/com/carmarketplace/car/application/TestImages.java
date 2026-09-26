package com.carmarketplace.car.application;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.CRC32;

public final class TestImages {

    public static final byte[] WEBP_1X1 = Base64.getDecoder()
            .decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

    private TestImages() {
    }

    public static byte[] jpeg(int width, int height) {
        return write(paint(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)), "jpg");
    }

    public static byte[] transparentPng(int width, int height) {
        return write(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    public static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = ByteBuffer.allocate(26)
                .put(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8})
                .putShort((short) 1)
                .putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0)
                .putInt(0)
                .array();
        byte[] app1 = ByteBuffer.allocate(4 + 6 + tiff.length)
                .put((byte) 0xFF).put((byte) 0xE1).putShort((short) (2 + 6 + tiff.length))
                .put("Exif".getBytes(StandardCharsets.US_ASCII)).put((byte) 0).put((byte) 0)
                .put(tiff)
                .array();
        int app0Length = ((jpeg[4] & 0xFF) << 8) | (jpeg[5] & 0xFF);
        int insertAt = 4 + app0Length;
        return ByteBuffer.allocate(jpeg.length + app1.length)
                .put(jpeg, 0, insertAt)
                .put(app1)
                .put(jpeg, insertAt, jpeg.length - insertAt)
                .array();
    }

    public static byte[] pngHeaderOnly(int width, int height) {
        byte[] ihdr = ByteBuffer.allocate(17)
                .put("IHDR".getBytes(StandardCharsets.US_ASCII))
                .putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0)
                .array();
        CRC32 crc = new CRC32();
        crc.update(ihdr);
        return ByteBuffer.allocate(8 + 4 + ihdr.length + 4)
                .put(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
                .putInt(13)
                .put(ihdr)
                .putInt((int) crc.getValue())
                .array();
    }

    public static byte[] heicHeader() {
        return ByteBuffer.allocate(24)
                .putInt(24)
                .put("ftypheic".getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .put("mif1heic".getBytes(StandardCharsets.US_ASCII))
                .array();
    }

    public static BufferedImage read(byte[] image) {
        try {
            return ImageIO.read(new ByteArrayInputStream(image));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean containsExif(byte[] image) {
        return new String(image, StandardCharsets.ISO_8859_1).contains("Exif");
    }

    private static BufferedImage paint(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setPaint(new GradientPaint(0, 0, Color.BLUE, image.getWidth(), image.getHeight(), Color.ORANGE));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    private static byte[] write(BufferedImage image, String format) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output.toByteArray();
    }
}
