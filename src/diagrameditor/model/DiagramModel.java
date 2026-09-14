package diagrameditor.model;

import java.util.*;

public class DiagramModel {
    private final LinkedHashMap<String, DiagramNode> nodes = new LinkedHashMap<>();
    private final ArrayList<DiagramEdge> edges = new ArrayList<>();
    private String direction = "LR";
    private DiagramType diagramType = DiagramType.FLOWCHART;

    public Map<String, DiagramNode> getNodes() { return Collections.unmodifiableMap(nodes); }
    public List<DiagramEdge> getEdges() { return Collections.unmodifiableList(edges); }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public DiagramType getDiagramType() { return diagramType; }
    public void setDiagramType(DiagramType diagramType) { this.diagramType = diagramType; }

    public DiagramNode getNode(String id) { return nodes.get(id); }
    public void addNode(DiagramNode node) { nodes.put(node.getId(), node); }
    public void addEdge(DiagramEdge edge) { edges.add(edge); }

    public void removeNode(DiagramNode node) {
        nodes.remove(node.getId());
        edges.removeIf(e -> e.getFrom() == node || e.getTo() == node);
    }

    public void removeEdge(DiagramEdge edge) { edges.remove(edge); }
}
