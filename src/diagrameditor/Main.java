package diagrameditor;

import diagrameditor.model.*;
import diagrameditor.parser.MermaidParser;
import diagrameditor.ui.MainFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--self-test")) {
            testFlowchart();
            testClassDiagram();
            testFontSizes();
            testEditorArchitectureSample();
            System.out.println("Self-test OK: flowchart + classDiagram + UML edge mappings + font sizes + editor architecture sample (v1.0)");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new MainFrame().setVisible(true);
        });
    }

    private static void testFlowchart() {
        String sample = """
                flowchart LR
                A[Start] --> B[Login]
                B --> C{Valid?}
                C -->|Yes| D[Main]
                C -->|No| E[Error]
                """;
        DiagramModel model = MermaidParser.parse(sample);
        if (model.getDiagramType() != DiagramType.FLOWCHART) throw new IllegalStateException("Flowchart type detection failed");
        if (model.getNodes().size() != 5 || model.getEdges().size() != 4) throw new IllegalStateException("Flowchart node/edge count failed");
        if (model.getEdges().get(0).getEdgeType() != EdgeType.FLOW) throw new IllegalStateException("--> must create FLOW edge");
    }

    private static void testClassDiagram() {
        String sample = """
                classDiagram
                direction LR
                class User {
                  -String id
                  +login()
                }
                class Admin {
                  +manageUsers()
                }
                class Session
                class Auditable
                User <|-- Admin : inherits
                User *-- Session : owns
                Admin ..|> Auditable : implements
                """;
        DiagramModel model = MermaidParser.parse(sample);
        if (model.getDiagramType() != DiagramType.CLASS_DIAGRAM) throw new IllegalStateException("Class diagram type detection failed");
        if (model.getNodes().size() != 4 || model.getEdges().size() != 3) throw new IllegalStateException("Class diagram node/edge count failed");
        DiagramNode user = model.getNode("User");
        if (user == null || user.getAttributes().size() != 1 || user.getMethods().size() != 1) throw new IllegalStateException("Class member parsing failed");
        if (model.getEdges().get(0).getEdgeType() != EdgeType.INHERITANCE) throw new IllegalStateException("Inheritance mapping failed");
        if (model.getEdges().get(1).getEdgeType() != EdgeType.COMPOSITION) throw new IllegalStateException("Composition mapping failed");
        if (model.getEdges().get(2).getEdgeType() != EdgeType.REALIZATION) throw new IllegalStateException("Realization mapping failed");
    }

    private static void testFontSizes() {
        DiagramNode node = new DiagramNode("N", "Node", ShapeType.RECTANGLE);
        node.setFontSize(24);
        if (node.getFontSize() != 24) throw new IllegalStateException("Node font size failed");
        DiagramEdge edge = new DiagramEdge(node, node, "label", EdgeType.ASSOCIATION);
        edge.setLabelFontSize(18);
        if (edge.getLabelFontSize() != 18) throw new IllegalStateException("Edge label font size failed");
    }

    private static void testEditorArchitectureSample() {
        String sample = diagrameditor.ui.MainFrame.APP_CLASS_EXAMPLE;
        DiagramModel model = MermaidParser.parse(sample);
        if (model.getDiagramType() != DiagramType.CLASS_DIAGRAM) throw new IllegalStateException("Editor architecture sample type failed");
        if (model.getNodes().size() < 10 || model.getEdges().size() < 10) throw new IllegalStateException("Editor architecture sample parsing failed");
    }
}
