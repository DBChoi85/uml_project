package diagrameditor.model;

public enum DiagramType {
    FLOWCHART("Flowchart"),
    CLASS_DIAGRAM("Class Diagram");

    private final String displayName;

    DiagramType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
