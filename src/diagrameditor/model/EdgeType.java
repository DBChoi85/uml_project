package diagrameditor.model;

public enum EdgeType {
    FLOW("Flow Arrow"),
    ASSOCIATION("Association"),
    DIRECTED_ASSOCIATION("Directed Association"),
    DEPENDENCY("Dependency"),
    INHERITANCE("Inheritance / Generalization"),
    REALIZATION("Realization"),
    AGGREGATION("Aggregation"),
    COMPOSITION("Composition");

    private final String displayName;

    EdgeType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
