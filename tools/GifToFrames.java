import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GifToFrames {

    private static final int DISPOSE_KEEP = 0;
    private static final int DISPOSE_BACKGROUND = 1;
    private static final int DISPOSE_PREVIOUS = 2;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("usage: java GifToFrames.java <in.gif> <out-dir> [maxSize]");
            return;
        }

        File input = new File(args[0]);
        File outputDir = new File(args[1]);
        int maxSize = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        outputDir.mkdirs();

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            System.err.println("no Gif decoder available");
            return;
        }

        ImageReader reader = readers.next();

        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            reader.setInput(stream);

            int frames = reader.getNumImages(true);
            int[] screen = logicalScreen(reader);

            int[] canvasSize = scaledSize(screen[0], screen[1], maxSize);

            System.out.println("frames: " + frames);
            System.out.println("Gif screen: " + screen[0] + "x" + screen[1]);
            System.out.println("Canvas: " + canvasSize[0] + "x" + canvasSize[1]);

            BufferedImage screenCanvas = new BufferedImage(
                screen[0], screen[1], BufferedImage.TYPE_INT_ARGB
            );

            Graphics2D g = screenCanvas.createGraphics();
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );

            int deltaFrames = 0;

            for (int i = 0; i < frames; i++) {
                FrameInfo info = readFrameInfo(reader, i);
                BufferedImage raw = reader.read(i);

                int width = raw.getWidth();
                int height = raw.getHeight();

                if (info.left != 0 || info.top != 0
                    || width < screen[0] || height < screen[1]) {
                    deltaFrames++;
                }

                BufferedImage undo = info.disposal == DISPOSE_PREVIOUS
                    ? crop(screenCanvas, info.left, info.top, width, height)
                    : null;

                drawAt(g, raw, info.left, info.top, AlphaComposite.SrcOver);
                writeFrame(screenCanvas, outputDir, i, canvasSize[0], canvasSize[1]);

                if (info.disposal == DISPOSE_BACKGROUND) {
                    g.clearRect(info.left, info.top, width, height);
                } else if (info.disposal == DISPOSE_PREVIOUS) {
                    drawAt(g, undo, info.left, info.top, AlphaComposite.Src);
                }
            }

            g.dispose();

            System.out.println("Frame deltas composited: " + deltaFrames + " of " + frames);
            removeStaleFrames(outputDir, frames);
        } finally {
            reader.dispose();
        }
    }

    private static void writeFrame(
            BufferedImage screen, File outputDir, int index, int width, int height)
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

        ImageIO.write(out, "png", new File(outputDir, "frame_%03d.png".formatted(index)));
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
        return raw == null ? fallback : (int) Long.parseLong(raw);
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

    private static void removeStaleFrames(File outputDir, int frames) {
        File[] files = outputDir.listFiles(
            (dir, name) -> name.matches("frame_\\d{3,}\\.png")
        );

        if (files == null) {
            return;
        }

        for (File file : files) {
            int index = Integer.parseInt(
                file.getName().substring(6, file.getName().lastIndexOf('.'))
            );

            if (index >= frames && file.delete()) {
                System.out.println("removed stale " + file.getName());
            }
        }
    }

    private static int[] scaledSize(int width, int height, int maxSize) {
        if (maxSize > 0 && Math.max(width, height) > maxSize) {
            double scale = (double) maxSize / Math.max(width, height);
            width = Math.max(1, (int) Math.round(width * scale));
            height = Math.max(1, (int) Math.round(height * scale));
        }

        return new int[] { width, height };
    }

    private static class FrameInfo {
        int left;
        int top;
        int disposal;
    }
}
