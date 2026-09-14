package diagrameditor.model;

public enum ShapeType {
    RECTANGLE("Rectangle"),
    ROUNDED("Rounded"),
    DIAMOND("Diamond"),
    CLASS_BOX("Class Box");

    private final String displayName;

    ShapeType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
