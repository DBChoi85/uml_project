package diagrameditor.model;

import java.util.UUID;

public class DiagramEdge {
    private final String id;
    private DiagramNode from;
    private DiagramNode to;
    private String label;
    private EdgeType edgeType;
    private int labelFontSize = 14;

    public DiagramEdge(DiagramNode from, DiagramNode to, String label, EdgeType edgeType) {
        this.id = UUID.randomUUID().toString();
        this.from = from;
        this.to = to;
        this.label = label == null ? "" : label;
        this.edgeType = edgeType == null ? EdgeType.FLOW : edgeType;
    }

    public DiagramEdge(DiagramNode from, DiagramNode to, String label, boolean directed) {
        this(from, to, label, directed ? EdgeType.FLOW : EdgeType.ASSOCIATION);
    }

    public String getId() { return id; }
    public DiagramNode getFrom() { return from; }
    public void setFrom(DiagramNode from) { this.from = from; }
    public DiagramNode getTo() { return to; }
    public void setTo(DiagramNode to) { this.to = to; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label == null ? "" : label; }
    public EdgeType getEdgeType() { return edgeType; }
    public void setEdgeType(EdgeType edgeType) { this.edgeType = edgeType == null ? EdgeType.FLOW : edgeType; }
    public int getLabelFontSize() { return labelFontSize; }
    public void setLabelFontSize(int labelFontSize) { this.labelFontSize = Math.max(8, Math.min(48, labelFontSize)); }

    public boolean isDirected() {
        return edgeType != EdgeType.ASSOCIATION && edgeType != EdgeType.AGGREGATION && edgeType != EdgeType.COMPOSITION;
    }

    public void setDirected(boolean directed) {
        this.edgeType = directed ? EdgeType.FLOW : EdgeType.ASSOCIATION;
    }
}
