package diagrameditor.export;

import diagrameditor.ui.DiagramCanvas;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class ImageExporter {
    private ImageExporter() {}

    public static void export(DiagramCanvas canvas, File file, String format) throws IOException {
        Dimension size = canvas.getExportDimension();
        int width = size.width;
        int height = size.height;
        int imageType = format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg")
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        BufferedImage image = new BufferedImage(width, height, imageType);
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            canvas.paintForExport(g2, width, height);
        } finally {
            g2.dispose();
        }
        ImageIO.write(image, format, file);
    }
}
