package diagrameditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DiagramNode {
    private final String id;
    private String text;
    private ShapeType shapeType;
    private double x;
    private double y;
    private double width;
    private double height;
    private int fontSize = 16;
    private final ArrayList<String> attributes = new ArrayList<>();
    private final ArrayList<String> methods = new ArrayList<>();

    public DiagramNode(String id, String text, ShapeType shapeType) {
        this.id = id;
        this.text = text;
        this.shapeType = shapeType;
        resetDefaultSizeForShape();
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text == null ? "" : text; }
    public ShapeType getShapeType() { return shapeType; }

    public void setShapeType(ShapeType shapeType) {
        this.shapeType = shapeType == null ? ShapeType.RECTANGLE : shapeType;
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public int getFontSize() { return fontSize; }
    public void setFontSize(int fontSize) {
        this.fontSize = Math.max(8, Math.min(72, fontSize));
        ensureClassSize();
    }

    public List<String> getAttributes() { return Collections.unmodifiableList(attributes); }
    public List<String> getMethods() { return Collections.unmodifiableList(methods); }

    public void setAttributes(List<String> values) {
        attributes.clear();
        if (values != null) {
            values.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(attributes::add);
        }
        ensureClassSize();
    }

    public void setMethods(List<String> values) {
        methods.clear();
        if (values != null) {
            values.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(methods::add);
        }
        ensureClassSize();
    }

    public void addAttribute(String value) {
        if (value != null && !value.isBlank()) attributes.add(value.trim());
        ensureClassSize();
    }

    public void addMethod(String value) {
        if (value != null && !value.isBlank()) methods.add(value.trim());
        ensureClassSize();
    }

    public double centerX() { return x + width / 2.0; }
    public double centerY() { return y + height / 2.0; }

    public void resetDefaultSizeForShape() {
        if (shapeType == ShapeType.DIAMOND) {
            width = 140;
            height = 90;
        } else if (shapeType == ShapeType.CLASS_BOX) {
            width = 230;
            height = 130;
        } else {
            width = 150;
            height = 64;
        }
    }

    public void ensureClassSize() {
        if (shapeType != ShapeType.CLASS_BOX) return;
        int memberFont = Math.max(9, fontSize - 2);
        double titleH = fontSize + 20.0;
        double lineH = memberFont + 7.0;
        width = Math.max(width, 230);
        double minHeight = titleH
                + Math.max(1, attributes.size()) * lineH + 10
                + Math.max(1, methods.size()) * lineH + 10;
        height = Math.max(height, minHeight);
    }

    @Override
    public String toString() {
        return id + " — " + text;
    }
}
