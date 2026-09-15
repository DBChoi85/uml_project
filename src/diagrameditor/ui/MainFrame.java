package diagrameditor.ui;

import diagrameditor.export.ImageExporter;
import diagrameditor.model.*;
import diagrameditor.parser.MermaidParser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public class MainFrame extends JFrame {
    public static final String FLOW_EXAMPLE = """
            flowchart LR
            A[Start] --> B[Login]
            B --> C{Valid?}
            C -->|Yes| D[Main]
            C -->|No| E[Error]
            """;

    public static final String CLASS_EXAMPLE = """
            classDiagram
            direction LR
            class User {
              -String id
              -String name
              +login()
              +logout()
            }
            class Admin {
              +manageUsers()
            }
            class Session {
              -String token
              +isValid()
            }
            class Auditable {
              +writeAuditLog()
            }
            User <|-- Admin : inherits
            User *-- Session : owns
            Admin ..|> Auditable : implements
            """;

    public static final String APP_CLASS_EXAMPLE = """
            classDiagram
            direction LR
            class Main
            class MainFrame
            class DiagramCanvas
            class MermaidParser
            class DiagramModel
            class DiagramNode
            class DiagramEdge
            class ImageExporter
            class ShapeType
            class EdgeType
            class PortSide
            Main --> MainFrame : starts
            MainFrame *-- DiagramCanvas : owns
            MainFrame --> MermaidParser : parses
            MainFrame --> ImageExporter : exports
            MermaidParser --> DiagramModel : creates
            DiagramModel *-- DiagramNode : contains
            DiagramModel *-- DiagramEdge : contains
            DiagramNode --> ShapeType : uses
            DiagramEdge --> EdgeType : uses
            DiagramEdge --> PortSide : anchors
            DiagramCanvas --> DiagramModel : renders
            """;

    private final JTextArea sourceArea = new JTextArea(FLOW_EXAMPLE, 15, 25);
    private final DiagramCanvas canvas = new DiagramCanvas();
    private final JLabel status = new JLabel("Ready");
    private final JComboBox<String> layoutCombo = new JComboBox<>(new String[]{"LR", "TD", "TB", "RL", "BT"});
    private final JCheckBox orthogonalBox = new JCheckBox("Orthogonal", true);

    private final CardLayout propertyCards = new CardLayout();
    private final JPanel propertyCardPanel = new JPanel(propertyCards);

    private final JTextField nodeId = new JTextField();
    private final JTextField nodeText = new JTextField();
    private final JComboBox<ShapeType> nodeShape = new JComboBox<>(ShapeType.values());
    private final JSpinner nodeFont = new JSpinner(new SpinnerNumberModel(16, 8, 72, 1));
    private final JSpinner nodeX = new JSpinner(new SpinnerNumberModel(0.0, -100000.0, 100000.0, 1.0));
    private final JSpinner nodeY = new JSpinner(new SpinnerNumberModel(0.0, -100000.0, 100000.0, 1.0));
    private final JSpinner nodeW = new JSpinner(new SpinnerNumberModel(150.0, 40.0, 10000.0, 1.0));
    private final JSpinner nodeH = new JSpinner(new SpinnerNumberModel(64.0, 30.0, 10000.0, 1.0));
    private final JTextArea nodeAttributes = new JTextArea(4, 18);
    private final JTextArea nodeMethods = new JTextArea(4, 18);

    private final JComboBox<DiagramNode> edgeFrom = new JComboBox<>();
    private final JComboBox<DiagramNode> edgeTo = new JComboBox<>();
    private final JComboBox<EdgeType> edgeType = new JComboBox<>(EdgeType.values());
    private final JTextField edgeLabel = new JTextField();
    private final JSpinner edgeFont = new JSpinner(new SpinnerNumberModel(14, 8, 48, 1));
    private final JSpinner edgeOffset = new JSpinner(new SpinnerNumberModel(0.0, -10000.0, 10000.0, 1.0));

    public MainFrame() {
        super("Mermaid Editable Diagram Editor");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setSize(1380, 860);
        setLocationRelativeTo(null);

        setJMenuBar(buildMenu());
        add(buildRoot(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        canvas.setSelectionListener(this::onSelectionChanged);
        generateDiagram();
        SwingUtilities.invokeLater(canvas::fitToView);
    }

    private JComponent buildRoot() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildLeftPanel(), buildCenterPanel());
        split.setResizeWeight(0.0);
        split.setDividerLocation(330);
        split.setContinuousLayout(true);
        return split;
    }

    private JComponent buildLeftPanel() {
        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.setBorder(new EmptyBorder(10, 10, 10, 6));
        left.setPreferredSize(new Dimension(330, 700));
        left.setMinimumSize(new Dimension(300, 300));

        JPanel sourcePanel = new JPanel(new BorderLayout(5, 5));
        sourcePanel.add(new JLabel("Mermaid-like code"), BorderLayout.NORTH);
        sourceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        sourceArea.setLineWrap(false);
        sourcePanel.add(new JScrollPane(sourceArea), BorderLayout.CENTER);

        JPanel sampleButtons = new JPanel(new GridLayout(2, 2, 5, 5));
        sampleButtons.add(button("Generate", e -> generateDiagram()));
        sampleButtons.add(button("Flow", e -> loadExample(FLOW_EXAMPLE)));
        sampleButtons.add(button("Class", e -> loadExample(CLASS_EXAMPLE)));
        sampleButtons.add(button("App Class", e -> loadExample(APP_CLASS_EXAMPLE)));
        sourcePanel.add(sampleButtons, BorderLayout.SOUTH);
        sourcePanel.setPreferredSize(new Dimension(310, 330));

        left.add(sourcePanel, BorderLayout.NORTH);
        buildPropertyCards();
        JScrollPane propsScroll = new JScrollPane(propertyCardPanel);
        propsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        propsScroll.getVerticalScrollBar().setUnitIncrement(14);
        left.add(propsScroll, BorderLayout.CENTER);
        return left;
    }

    private JComponent buildCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.setBorder(new EmptyBorder(10, 6, 10, 10));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        toolbar.add(button("Fit", e -> canvas.fitToView()));
        toolbar.add(button("100%", e -> canvas.resetView()));
        toolbar.add(button("−", e -> canvas.zoomBy(1 / 1.15)));
        toolbar.add(button("+", e -> canvas.zoomBy(1.15)));
        toolbar.add(new JLabel("Layout"));
        toolbar.add(layoutCombo);
        toolbar.add(button("Relayout", e -> relayout()));
        orthogonalBox.addActionListener(e -> canvas.setOrthogonal(orthogonalBox.isSelected()));
        toolbar.add(orthogonalBox);
        toolbar.add(button("PNG", e -> exportImage("png")));
        toolbar.add(button("JPG", e -> exportImage("jpg")));
        center.add(toolbar, BorderLayout.NORTH);

        center.add(canvas, BorderLayout.CENTER);

        JLabel hint = new JLabel("Wheel: zoom · blank drag: pan · node drag: move · blue handles: resize · edge endpoints: drag to magnetic ports · orange handle: move orthogonal route");
        hint.setBorder(new EmptyBorder(3, 3, 0, 3));
        center.add(hint, BorderLayout.SOUTH);
        return center;
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open Mermaid...");
        open.addActionListener(e -> openSource());
        JMenuItem save = new JMenuItem("Save Mermaid...");
        save.addActionListener(e -> saveSource());
        JMenuItem png = new JMenuItem("Export PNG...");
        png.addActionListener(e -> exportImage("png"));
        JMenuItem jpg = new JMenuItem("Export JPG...");
        jpg.addActionListener(e -> exportImage("jpg"));
        file.add(open); file.add(save); file.addSeparator(); file.add(png); file.add(jpg);
        bar.add(file);
        return bar;
    }

    private void buildPropertyCards() {
        JPanel empty = new JPanel(new BorderLayout());
        JLabel info = new JLabel("<html>Select a node or edge.<br><br>Each object has 5 magnetic ports per side (20 total).<br>Select an edge and drag either blue endpoint to reconnect it.</html>");
        info.setBorder(new EmptyBorder(12, 8, 8, 8));
        empty.add(info, BorderLayout.NORTH);

        propertyCardPanel.add(empty, "EMPTY");
        propertyCardPanel.add(buildNodeEditor(), "NODE");
        propertyCardPanel.add(buildEdgeEditor(), "EDGE");
        propertyCards.show(propertyCardPanel, "EMPTY");
    }

    private JComponent buildNodeEditor() {
        JPanel p = verticalPanel();
        p.add(sectionTitle("Selected object"));
        nodeId.setEditable(false);
        addField(p, "Object ID", nodeId);
        addField(p, "Text / Class name", nodeText);
        addField(p, "Shape", nodeShape);
        addField(p, "Font size", nodeFont);
        addField(p, "X", nodeX);
        addField(p, "Y", nodeY);
        addField(p, "Width", nodeW);
        addField(p, "Height", nodeH);
        addField(p, "Attributes (one per line)", new JScrollPane(nodeAttributes));
        addField(p, "Methods (one per line)", new JScrollPane(nodeMethods));
        JPanel buttons = new JPanel(new GridLayout(1, 2, 5, 0));
        buttons.add(button("Apply", e -> applyNode()));
        buttons.add(button("Delete", e -> canvas.deleteSelection()));
        p.add(buttons);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JComponent buildEdgeEditor() {
        JPanel p = verticalPanel();
        p.add(sectionTitle("Selected edge"));
        addField(p, "From object", edgeFrom);
        addField(p, "To object", edgeTo);
        addField(p, "Edge type", edgeType);
        addField(p, "Label", edgeLabel);
        addField(p, "Label font size", edgeFont);
        addField(p, "Route offset", edgeOffset);
        JLabel hint = new JLabel("<html><small>Tip: drag either blue endpoint to one of the 20 magnetic ports. Drag the orange handle to move the orthogonal middle segment.</small></html>");
        hint.setBorder(new EmptyBorder(5, 0, 8, 0));
        p.add(hint);
        JPanel buttons = new JPanel(new GridLayout(1, 3, 5, 0));
        buttons.add(button("Apply", e -> applyEdge()));
        buttons.add(button("Reset route", e -> resetEdgeRoute()));
        buttons.add(button("Delete", e -> canvas.deleteSelection()));
        p.add(buttons);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel verticalPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(8, 8, 8, 8));
        return p;
    }

    private JComponent sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    private void addField(JPanel p, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field instanceof JScrollPane ? 88 : 30));
        p.add(l);
        p.add(Box.createVerticalStrut(3));
        p.add(field);
        p.add(Box.createVerticalStrut(8));
    }

    private JButton button(String label, java.awt.event.ActionListener listener) {
        JButton b = new JButton(label);
        b.addActionListener(listener);
        return b;
    }

    private void loadExample(String text) {
        sourceArea.setText(text);
        generateDiagram();
        SwingUtilities.invokeLater(canvas::fitToView);
    }

    private void generateDiagram() {
        try {
            DiagramModel model = MermaidParser.parse(sourceArea.getText());
            canvas.setModel(model);
            layoutCombo.setSelectedItem(displayDirection(model.getDirection()));
            status.setText(model.getNodes().size() + " nodes, " + model.getEdges().size() + " edges");
        } catch (Exception ex) {
            showError("Parse error", ex);
        }
    }

    private String displayDirection(String d) {
        return "TB".equalsIgnoreCase(d) ? "TD" : d;
    }

    private void relayout() {
        try {
            String direction = String.valueOf(layoutCombo.getSelectedItem());
            MermaidParser.relayout(canvas.getModel(), direction);
            canvas.repaint();
            canvas.fitToView();
            status.setText("Relayout: " + direction);
        } catch (Exception ex) {
            showError("Layout error", ex);
        }
    }

    private void onSelectionChanged(Object selection) {
        if (selection instanceof DiagramNode node) {
            nodeId.setText(node.getId());
            nodeText.setText(node.getText());
            nodeShape.setSelectedItem(node.getShapeType());
            nodeFont.setValue(node.getFontSize());
            nodeX.setValue(node.getX()); nodeY.setValue(node.getY());
            nodeW.setValue(node.getWidth()); nodeH.setValue(node.getHeight());
            nodeAttributes.setText(String.join("\n", node.getAttributes()));
            nodeMethods.setText(String.join("\n", node.getMethods()));
            propertyCards.show(propertyCardPanel, "NODE");
        } else if (selection instanceof DiagramEdge edge) {
            refreshNodeCombos();
            edgeFrom.setSelectedItem(edge.getFrom());
            edgeTo.setSelectedItem(edge.getTo());
            edgeType.setSelectedItem(edge.getEdgeType());
            edgeLabel.setText(edge.getLabel());
            edgeFont.setValue(edge.getLabelFontSize());
            edgeOffset.setValue(edge.getRouteOffset());
            propertyCards.show(propertyCardPanel, "EDGE");
        } else {
            propertyCards.show(propertyCardPanel, "EMPTY");
        }
    }

    private void refreshNodeCombos() {
        DiagramNode fromSelected = (DiagramNode) edgeFrom.getSelectedItem();
        DiagramNode toSelected = (DiagramNode) edgeTo.getSelectedItem();
        DefaultComboBoxModel<DiagramNode> m1 = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<DiagramNode> m2 = new DefaultComboBoxModel<>();
        for (DiagramNode node : canvas.getModel().getNodes().values()) { m1.addElement(node); m2.addElement(node); }
        edgeFrom.setModel(m1); edgeTo.setModel(m2);
        if (fromSelected != null) edgeFrom.setSelectedItem(fromSelected);
        if (toSelected != null) edgeTo.setSelectedItem(toSelected);
    }

    private void applyNode() {
        if (!(canvas.getSelection() instanceof DiagramNode node)) return;
        node.setText(nodeText.getText());
        node.setShapeType((ShapeType) nodeShape.getSelectedItem());
        node.setFontSize((Integer) nodeFont.getValue());
        node.setX(((Number) nodeX.getValue()).doubleValue());
        node.setY(((Number) nodeY.getValue()).doubleValue());
        node.setWidth(((Number) nodeW.getValue()).doubleValue());
        node.setHeight(((Number) nodeH.getValue()).doubleValue());
        node.setAttributes(lines(nodeAttributes.getText()));
        node.setMethods(lines(nodeMethods.getText()));
        canvas.repaint();
        status.setText("Updated node " + node.getId());
    }

    private void applyEdge() {
        if (!(canvas.getSelection() instanceof DiagramEdge edge)) return;
        DiagramNode newFrom = (DiagramNode) edgeFrom.getSelectedItem();
        DiagramNode newTo = (DiagramNode) edgeTo.getSelectedItem();
        if (newFrom != edge.getFrom()) { edge.setFrom(newFrom); edge.clearFromPort(); }
        if (newTo != edge.getTo()) { edge.setTo(newTo); edge.clearToPort(); }
        edge.setEdgeType((EdgeType) edgeType.getSelectedItem());
        edge.setLabel(edgeLabel.getText());
        edge.setLabelFontSize((Integer) edgeFont.getValue());
        edge.setRouteOffset(((Number) edgeOffset.getValue()).doubleValue());
        canvas.repaint();
        status.setText("Updated edge");
    }

    private void resetEdgeRoute() {
        if (!(canvas.getSelection() instanceof DiagramEdge edge)) return;
        edge.resetRouteOffset();
        edge.clearPorts();
        edgeOffset.setValue(0.0);
        canvas.repaint();
        status.setText("Edge route reset to automatic ports");
    }

    private List<String> lines(String text) {
        return Arrays.stream(text.split("\\R")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    private void openSource() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            sourceArea.setText(Files.readString(fc.getSelectedFile().toPath(), StandardCharsets.UTF_8));
            generateDiagram();
            SwingUtilities.invokeLater(canvas::fitToView);
        } catch (Exception ex) { showError("Open failed", ex); }
    }

    private void saveSource() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Files.writeString(fc.getSelectedFile().toPath(), sourceArea.getText(), StandardCharsets.UTF_8);
            status.setText("Saved " + fc.getSelectedFile().getName());
        } catch (Exception ex) { showError("Save failed", ex); }
    }

    private void exportImage(String format) {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("diagram." + format));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith("." + format)) file = new File(file.getParentFile(), file.getName() + "." + format);
        try {
            ImageExporter.export(canvas, file, format);
            status.setText("Exported " + file.getName());
        } catch (Exception ex) { showError("Export failed", ex); }
    }

    private void showError(String title, Exception ex) {
        status.setText(title + ": " + ex.getMessage());
        JOptionPane.showMessageDialog(this, ex.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
