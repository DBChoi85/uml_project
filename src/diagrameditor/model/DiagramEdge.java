package diagrameditor.model;

import java.util.UUID;

public class DiagramEdge {
    private final String id;
    private DiagramNode from;
    private DiagramNode to;
    private String label;
    private EdgeType edgeType;
    private int labelFontSize = 14;

    // Endpoint attachment. When fixed=false the canvas chooses the nearest port automatically.
    private boolean fromPortFixed;
    private boolean toPortFixed;
    private PortSide fromSide = PortSide.RIGHT;
    private PortSide toSide = PortSide.LEFT;
    private int fromPortIndex = 2;
    private int toPortIndex = 2;

    // Manual displacement of the central orthogonal segment from its automatic midpoint.
    private double routeOffset;

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

    public boolean isFromPortFixed() { return fromPortFixed; }
    public boolean isToPortFixed() { return toPortFixed; }
    public PortSide getFromSide() { return fromSide; }
    public PortSide getToSide() { return toSide; }
    public int getFromPortIndex() { return fromPortIndex; }
    public int getToPortIndex() { return toPortIndex; }

    public void attachFrom(DiagramNode node, PortSide side, int portIndex) {
        from = node;
        fromSide = side == null ? PortSide.RIGHT : side;
        fromPortIndex = clampPortIndex(portIndex);
        fromPortFixed = true;
    }

    public void attachTo(DiagramNode node, PortSide side, int portIndex) {
        to = node;
        toSide = side == null ? PortSide.LEFT : side;
        toPortIndex = clampPortIndex(portIndex);
        toPortFixed = true;
    }

    public void clearFromPort() { fromPortFixed = false; }
    public void clearToPort() { toPortFixed = false; }
    public void clearPorts() {
        fromPortFixed = false;
        toPortFixed = false;
    }

    public double getRouteOffset() { return routeOffset; }
    public void setRouteOffset(double routeOffset) { this.routeOffset = routeOffset; }
    public void resetRouteOffset() { routeOffset = 0.0; }

    public boolean isDirected() {
        return edgeType != EdgeType.ASSOCIATION && edgeType != EdgeType.AGGREGATION && edgeType != EdgeType.COMPOSITION;
    }

    public void setDirected(boolean directed) {
        this.edgeType = directed ? EdgeType.FLOW : EdgeType.ASSOCIATION;
    }

    private static int clampPortIndex(int index) {
        return Math.max(0, Math.min(4, index));
    }
}
