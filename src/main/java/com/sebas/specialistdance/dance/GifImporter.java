package com.sebas.specialistdance.dance;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class GifImporter {

    private static final int DISPOSE_KEEP = 0;
    private static final int DISPOSE_BACKGROUND = 1;
    private static final int DISPOSE_PREVIOUS = 2;

    private static final int MAX_FRAMES = 2_000;
    private static final long MAX_BYTES = 50L * 1024 * 1024;
    private static final long MAX_PIXELS = 16_000_000L;
    private static final int MAX_DIMENSION = 512;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GifImporter() {}

    public static int convert(Path gif, Path outputDir, boolean debug) {
        try {
            if (!Files.isRegularFile(gif)) {
                log(debug, "not a file: " + gif);
                return -1;
            }

            long size = Files.size(gif);

            if (size > MAX_BYTES) {
                log(debug, "GIF is too large (" + size + " bytes): " + gif.getFileName());
                return -1;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");

            if (!readers.hasNext()) {
                log(debug, "no GIF decoder in this runtime");
                return -1;
            }

            ImageReader reader = readers.next();

            try (ImageInputStream stream = ImageIO.createImageInputStream(gif.toFile())) {
                if (stream == null) {
                    log(debug, "could not open " + gif.getFileName());
                    return -1;
                }

                reader.setInput(stream);

                int frames = reader.getNumImages(true);

                if (frames <= 0) {
                    log(debug, "GIF has no frames: " + gif.getFileName());
                    return -1;
                }

                if (frames > MAX_FRAMES) {
                    log(debug, "GIF has too many frames (" + frames + "): " + gif.getFileName());
                    return -1;
                }

                int[] screen = logicalScreen(reader);

                if (screen[0] <= 0 || screen[1] <= 0
                        || (long) screen[0] * screen[1] > MAX_PIXELS) {
                    log(debug, "GIF screen is invalid or too large ("
                        + screen[0] + "x" + screen[1] + "): " + gif.getFileName());
                    return -1;
                }

                int[] canvasSize = scaledSize(screen[0], screen[1], MAX_DIMENSION);

                BufferedImage screenCanvas = new BufferedImage(
                    screen[0], screen[1], BufferedImage.TYPE_INT_ARGB
                );

                Graphics2D g = screenCanvas.createGraphics();
                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                );

                long totalDelay = 0;
                int delayFrames = 0;

                for (int i = 0; i < frames; i++) {
                    FrameInfo info = readFrameInfo(reader, i);
                    BufferedImage raw = reader.read(i);

                    int width = raw.getWidth();
                    int height = raw.getHeight();

                    BufferedImage undo = info.disposal == DISPOSE_PREVIOUS
                        ? crop(screenCanvas, info.left, info.top, width, height)
                        : null;

                    drawAt(g, raw, info.left, info.top, AlphaComposite.SrcOver);
                    writeFrame(screenCanvas, outputDir, i, canvasSize[0], canvasSize[1]);

                    if (info.delayMs > 0) {
                        totalDelay += info.delayMs;
                        delayFrames++;
                    }

                    if (info.disposal == DISPOSE_BACKGROUND) {
                        g.clearRect(info.left, info.top, width, height);
                    } else if (info.disposal == DISPOSE_PREVIOUS) {
                        drawAt(g, undo, info.left, info.top, AlphaComposite.Src);
                    }
                }

                g.dispose();

                int frameDurationMs = delayFrames == 0
                    ? 100
                    : (int) Math.round((double) totalDelay / delayFrames);
                frameDurationMs = Math.clamp(frameDurationMs, 10, 10_000);
                writePackJson(outputDir, frameDurationMs);

                log(debug, "imported " + gif.getFileName() + ": " + frames + " frames, "
                    + canvasSize[0] + "x" + canvasSize[1] + ", " + frameDurationMs + " ms");

                return frames;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log(debug, "could not import " + gif.getFileName() + ": " + e.getMessage());
            return -1;
        }
    }

    private static void writeFrame(
            BufferedImage screen, Path outputDir, int index, int width, int height)
            throws IOException {

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        );
        g.setRenderingHint(
            RenderingHints.KEY_RENDERING,
            RenderingHints.VALUE_RENDER_QUALITY
        );
        g.drawImage(screen, 0, 0, width, height, null);
        g.dispose();

        ImageIO.write(out, "png", outputDir.resolve("frame_%03d.png".formatted(index)).toFile());
    }

    private static void writePackJson(Path outputDir, int frameDurationMs) throws IOException {
        Path file = outputDir.resolve("pack.json");

        if (Files.exists(file)) {
            return;
        }

        Files.writeString(file, GSON.toJson(Map.of("frameDurationMs", frameDurationMs)));
    }

    private static void drawAt(
            Graphics2D g, BufferedImage src, int x, int y, AlphaComposite rule) {

        Composite previous = g.getComposite();
        g.setComposite(rule);
        g.drawImage(src, x, y, null);
        g.setComposite(previous);
    }

    private static BufferedImage crop(BufferedImage src, int x, int y, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        drawAt(g, src, -x, -y, AlphaComposite.Src);
        g.dispose();
        return out;
    }

    private static int[] logicalScreen(ImageReader reader) throws IOException {
        Node screen = child(reader.getStreamMetadata()
            .getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");

        if (screen == null) {
            return new int[] { reader.getWidth(0), reader.getHeight(0) };
        }

        return new int[] {
            (int) Long.parseLong(value(screen, "logicalScreenWidth")),
            (int) Long.parseLong(value(screen, "logicalScreenHeight"))
        };
    }

    private static FrameInfo readFrameInfo(ImageReader reader, int index) throws IOException {
        Node tree = reader.getImageMetadata(index)
            .getAsTree("javax_imageio_gif_image_1.0");

        Node descriptor = child(tree, "ImageDescriptor");
        Node control = child(tree, "GraphicControlExtension");

        FrameInfo info = new FrameInfo();
        info.left = intAt(descriptor, "imageLeftPosition", 0);
        info.top = intAt(descriptor, "imageTopPosition", 0);
        info.delayMs = intAt(control, "delayTime", 0) * 10;
        info.disposal = disposal(control == null ? null : value(control, "disposalMethod"));

        return info;
    }

    private static int disposal(String method) {
        if ("restoreToBackgroundColor".equals(method)) {
            return DISPOSE_BACKGROUND;
        }

        if ("restoreToPrevious".equals(method)) {
            return DISPOSE_PREVIOUS;
        }

        return DISPOSE_KEEP;
    }

    private static int intAt(Node node, String attribute, int fallback) {
        String raw = node == null ? null : value(node, attribute);

        if (raw == null) {
            return fallback;
        }

        try {
            return (int) Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String value(Node node, String attribute) {
        NamedNodeMap attributes = node.getAttributes();

        if (attributes == null) {
            return null;
        }

        Node item = attributes.getNamedItem(attribute);
        return item == null ? null : item.getNodeValue();
    }

    private static Node child(Node parent, String name) {
        if (parent == null) {
            return null;
        }

        NodeList children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeName().equals(name)) {
                return child;
            }
        }

        return null;
    }

    private static int[] scaledSize(int width, int height, int maxSize) {
        if (maxSize > 0 && Math.max(width, height) > maxSize) {
            double scale = (double) maxSize / Math.max(width, height);
            width = Math.max(1, (int) Math.round(width * scale));
            height = Math.max(1, (int) Math.round(height * scale));
        }

        return new int[] { width, height };
    }

    private static void log(boolean debug, String message) {
        if (debug) {
            System.out.println("Specialist Dance: " + message);
        }
    }

    private static class FrameInfo {
        int left;
        int top;
        int delayMs;
        int disposal;
    }
}
