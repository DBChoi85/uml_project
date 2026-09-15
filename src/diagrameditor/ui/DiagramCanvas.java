package diagrameditor.ui;

import diagrameditor.model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.List;
import java.util.*;

/**
 * Interactive diagram canvas.
 *
 * Features:
 * - pan / wheel zoom
 * - node move / 8-handle resize
 * - orthogonal or straight edge routing
 * - 5 magnetic connection ports per object side (20 ports/object)
 * - edge endpoint drag-and-snap
 * - draggable central orthogonal segment
 */
public class DiagramCanvas extends JPanel {
    public interface SelectionListener {
        void selectionChanged(Object selection);
    }

    private enum DragMode { NONE, PAN, NODE, RESIZE, EDGE_FROM, EDGE_TO, EDGE_ROUTE }
    private enum ResizeHandle { NW, N, NE, E, SE, S, SW, W }

    private static final int PORTS_PER_SIDE = 5;
    private static final double MIN_NODE_W = 60;
    private static final double MIN_NODE_H = 40;
    private static final double SNAP_PIXELS = 18;
    private static final double HANDLE_PIXELS = 9;

    private DiagramModel model = new DiagramModel();
    private Object selection;
    private SelectionListener selectionListener;
    private boolean orthogonal = true;

    private double zoom = 1.0;
    private double panX = 40;
    private double panY = 40;

    private DragMode dragMode = DragMode.NONE;
    private Point dragScreenStart;
    private Point2D.Double dragWorldStart;
    private double startPanX, startPanY;
    private DiagramNode dragNode;
    private double nodeStartX, nodeStartY, nodeStartW, nodeStartH;
    private ResizeHandle resizeHandle;
    private DiagramEdge dragEdge;
    private double routeOffsetStart;
    private Point2D.Double floatingEndpoint;
    private PortHit snapCandidate;

    public DiagramCanvas() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(1000, 700));
        setFocusable(true);

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { onMousePressed(e); }
            @Override public void mouseDragged(MouseEvent e) { onMouseDragged(e); }
            @Override public void mouseReleased(MouseEvent e) { onMouseReleased(e); }
            @Override public void mouseMoved(MouseEvent e) { onMouseMoved(e); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { onMouseWheel(e); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    public DiagramModel getModel() { return model; }

    public void setModel(DiagramModel model) {
        this.model = model == null ? new DiagramModel() : model;
        setSelection(null);
        resetView();
        repaint();
    }

    public Object getSelection() { return selection; }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public boolean isOrthogonal() { return orthogonal; }
    public void setOrthogonal(boolean orthogonal) {
        this.orthogonal = orthogonal;
        repaint();
    }

    public double getZoom() { return zoom; }

    public void resetView() {
        zoom = 1.0;
        panX = 40;
        panY = 40;
        repaint();
    }

    public void fitToView() {
        Rectangle2D bounds = modelBounds();
        if (bounds == null || getWidth() <= 0 || getHeight() <= 0) return;
        double margin = 50;
        double zx = (getWidth() - margin * 2) / Math.max(1, bounds.getWidth());
        double zy = (getHeight() - margin * 2) / Math.max(1, bounds.getHeight());
        zoom = clampZoom(Math.min(zx, zy));
        panX = getWidth() / 2.0 - (bounds.getCenterX() * zoom);
        panY = getHeight() / 2.0 - (bounds.getCenterY() * zoom);
        repaint();
    }

    public void zoomBy(double factor) {
        zoomAt(new Point(getWidth() / 2, getHeight() / 2), zoom * factor);
    }

    public Dimension getExportDimension() {
        Rectangle2D bounds = modelBounds();
        if (bounds == null) return new Dimension(800, 600);
        int w = Math.max(200, (int) Math.ceil(bounds.getWidth() + 80));
        int h = Math.max(150, (int) Math.ceil(bounds.getHeight() + 80));
        return new Dimension(w, h);
    }

    public void paintForExport(Graphics2D g2, int width, int height) {
        Rectangle2D bounds = modelBounds();
        if (bounds == null) return;
        Object oldAA = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform old = g2.getTransform();
        g2.translate(40 - bounds.getX(), 40 - bounds.getY());
        paintWorld(g2, false);
        g2.setTransform(old);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintGrid(g2);
            g2.translate(panX, panY);
            g2.scale(zoom, zoom);
            paintWorld(g2, true);
        } finally {
            g2.dispose();
        }
    }

    private void paintGrid(Graphics2D g2) {
        g2.setColor(new Color(242, 244, 247));
        double grid = 20 * zoom;
        if (grid < 8) grid *= 2;
        double ox = mod(panX, grid);
        double oy = mod(panY, grid);
        for (double x = ox; x < getWidth(); x += grid) g2.draw(new Line2D.Double(x, 0, x, getHeight()));
        for (double y = oy; y < getHeight(); y += grid) g2.draw(new Line2D.Double(0, y, getWidth(), y));
    }

    private static double mod(double a, double b) {
        double r = a % b;
        return r < 0 ? r + b : r;
    }

    private void paintWorld(Graphics2D g2, boolean interactive) {
        for (DiagramEdge edge : model.getEdges()) paintEdge(g2, edge, interactive);
        for (DiagramNode node : model.getNodes().values()) paintNode(g2, node, interactive);

        if (!interactive) return;

        if (selection instanceof DiagramNode node) {
            paintResizeHandles(g2, node);
            paintPorts(g2, node, false);
        } else if (selection instanceof DiagramEdge edge) {
            paintEdgeHandles(g2, edge);
            paintPorts(g2, edge.getFrom(), false);
            if (edge.getTo() != edge.getFrom()) paintPorts(g2, edge.getTo(), false);
        }

        if (dragMode == DragMode.EDGE_FROM || dragMode == DragMode.EDGE_TO) {
            for (DiagramNode node : model.getNodes().values()) paintPorts(g2, node, true);
            if (floatingEndpoint != null) paintFloatingEdge(g2);
        }
    }

    private void paintNode(Graphics2D g2, DiagramNode node, boolean interactive) {
        Shape shape = nodeShape(node);
        g2.setColor(Color.WHITE);
        g2.fill(shape);
        g2.setStroke(new BasicStroke(selection == node && interactive ? 2.5f : 1.6f));
        g2.setColor(new Color(35, 39, 47));
        g2.draw(shape);

        if (node.getShapeType() == ShapeType.CLASS_BOX) {
            paintClassNode(g2, node);
        } else {
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, node.getFontSize()));
            g2.setColor(new Color(25, 28, 34));
            drawCenteredText(g2, node.getText(), node.centerX(), node.centerY());
        }
    }

    private void paintClassNode(Graphics2D g2, DiagramNode node) {
        double x = node.getX(), y = node.getY(), w = node.getWidth();
        int titleSize = node.getFontSize();
        int memberSize = Math.max(9, titleSize - 2);
        double titleH = titleSize + 20.0;
        double lineH = memberSize + 7.0;
        double attrH = Math.max(1, node.getAttributes().size()) * lineH + 10;

        g2.setColor(new Color(45, 49, 57));
        g2.draw(new Line2D.Double(x, y + titleH, x + w, y + titleH));
        g2.draw(new Line2D.Double(x, y + titleH + attrH, x + w, y + titleH + attrH));

        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, titleSize));
        drawCenteredText(g2, node.getText(), x + w / 2, y + titleH / 2 + 1);

        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, memberSize));
        double cy = y + titleH + memberSize + 7;
        if (node.getAttributes().isEmpty()) {
            g2.setColor(new Color(150, 150, 150));
            g2.drawString(" ", (float) (x + 9), (float) cy);
        } else {
            g2.setColor(new Color(30, 33, 38));
            for (String s : node.getAttributes()) {
                g2.drawString(s, (float) (x + 9), (float) cy);
                cy += lineH;
            }
        }

        cy = y + titleH + attrH + memberSize + 7;
        g2.setColor(new Color(30, 33, 38));
        for (String s : node.getMethods()) {
            g2.drawString(s, (float) (x + 9), (float) cy);
            cy += lineH;
        }
    }

    private void drawCenteredText(Graphics2D g2, String text, double cx, double cy) {
        FontMetrics fm = g2.getFontMetrics();
        String value = text == null ? "" : text;
        double tx = cx - fm.stringWidth(value) / 2.0;
        double ty = cy + (fm.getAscent() - fm.getDescent()) / 2.0;
        g2.drawString(value, (float) tx, (float) ty);
    }

    private Shape nodeShape(DiagramNode n) {
        return switch (n.getShapeType()) {
            case ROUNDED -> new RoundRectangle2D.Double(n.getX(), n.getY(), n.getWidth(), n.getHeight(), 24, 24);
            case DIAMOND -> {
                Path2D p = new Path2D.Double();
                p.moveTo(n.centerX(), n.getY());
                p.lineTo(n.getX() + n.getWidth(), n.centerY());
                p.lineTo(n.centerX(), n.getY() + n.getHeight());
                p.lineTo(n.getX(), n.centerY());
                p.closePath();
                yield p;
            }
            default -> new Rectangle2D.Double(n.getX(), n.getY(), n.getWidth(), n.getHeight());
        };
    }

    private void paintEdge(Graphics2D g2, DiagramEdge edge, boolean interactive) {
        EdgeGeometry geo = edgeGeometry(edge);
        Stroke old = g2.getStroke();
        if (edge.getEdgeType() == EdgeType.DEPENDENCY || edge.getEdgeType() == EdgeType.REALIZATION) {
            g2.setStroke(new BasicStroke(selection == edge && interactive ? 2.6f : 1.7f,
                    BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{8f, 6f}, 0));
        } else {
            g2.setStroke(new BasicStroke(selection == edge && interactive ? 2.6f : 1.7f));
        }
        g2.setColor(new Color(38, 42, 48));
        drawPolyline(g2, geo.points);
        g2.setStroke(old);

        paintMarkers(g2, edge, geo);
        paintEdgeLabel(g2, edge, geo);
    }

    private void drawPolyline(Graphics2D g2, List<Point2D.Double> points) {
        for (int i = 0; i < points.size() - 1; i++) {
            g2.draw(new Line2D.Double(points.get(i), points.get(i + 1)));
        }
    }

    private void paintMarkers(Graphics2D g2, DiagramEdge edge, EdgeGeometry geo) {
        List<Point2D.Double> pts = geo.points;
        if (pts.size() < 2) return;
        Point2D.Double start = pts.get(0);
        Point2D.Double startNext = pts.get(1);
        Point2D.Double end = pts.get(pts.size() - 1);
        Point2D.Double endPrev = pts.get(pts.size() - 2);

        switch (edge.getEdgeType()) {
            case FLOW -> drawArrow(g2, endPrev, end, true);
            case DIRECTED_ASSOCIATION -> drawArrow(g2, endPrev, end, false);
            case DEPENDENCY -> drawArrow(g2, endPrev, end, false);
            case INHERITANCE, REALIZATION -> drawTriangle(g2, endPrev, end);
            case AGGREGATION -> drawDiamondMarker(g2, startNext, start, false);
            case COMPOSITION -> drawDiamondMarker(g2, startNext, start, true);
            default -> { }
        }
    }

    private void drawArrow(Graphics2D g2, Point2D from, Point2D tip, boolean filled) {
        double angle = Math.atan2(tip.getY() - from.getY(), tip.getX() - from.getX());
        double len = 13, wing = 6;
        Path2D p = new Path2D.Double();
        p.moveTo(tip.getX(), tip.getY());
        p.lineTo(tip.getX() - len * Math.cos(angle) + wing * Math.sin(angle),
                tip.getY() - len * Math.sin(angle) - wing * Math.cos(angle));
        p.lineTo(tip.getX() - len * Math.cos(angle) - wing * Math.sin(angle),
                tip.getY() - len * Math.sin(angle) + wing * Math.cos(angle));
        p.closePath();
        if (filled) g2.fill(p); else g2.draw(p);
    }

    private void drawTriangle(Graphics2D g2, Point2D from, Point2D tip) {
        double angle = Math.atan2(tip.getY() - from.getY(), tip.getX() - from.getX());
        double len = 16, wing = 8;
        Path2D p = new Path2D.Double();
        p.moveTo(tip.getX(), tip.getY());
        p.lineTo(tip.getX() - len * Math.cos(angle) + wing * Math.sin(angle),
                tip.getY() - len * Math.sin(angle) - wing * Math.cos(angle));
        p.lineTo(tip.getX() - len * Math.cos(angle) - wing * Math.sin(angle),
                tip.getY() - len * Math.sin(angle) + wing * Math.cos(angle));
        p.closePath();
        Color old = g2.getColor();
        g2.setColor(Color.WHITE);
        g2.fill(p);
        g2.setColor(old);
        g2.draw(p);
    }

    private void drawDiamondMarker(Graphics2D g2, Point2D inward, Point2D tip, boolean filled) {
        double angle = Math.atan2(tip.getY() - inward.getY(), tip.getX() - inward.getX());
        double len = 18, half = 6;
        double bx = tip.getX() - len * Math.cos(angle);
        double by = tip.getY() - len * Math.sin(angle);
        Path2D p = new Path2D.Double();
        p.moveTo(tip.getX(), tip.getY());
        p.lineTo(tip.getX() - len / 2 * Math.cos(angle) + half * Math.sin(angle),
                tip.getY() - len / 2 * Math.sin(angle) - half * Math.cos(angle));
        p.lineTo(bx, by);
        p.lineTo(tip.getX() - len / 2 * Math.cos(angle) - half * Math.sin(angle),
                tip.getY() - len / 2 * Math.sin(angle) + half * Math.cos(angle));
        p.closePath();
        if (filled) g2.fill(p); else {
            Color old = g2.getColor();
            g2.setColor(Color.WHITE);
            g2.fill(p);
            g2.setColor(old);
            g2.draw(p);
        }
    }

    private void paintEdgeLabel(Graphics2D g2, DiagramEdge edge, EdgeGeometry geo) {
        if (edge.getLabel() == null || edge.getLabel().isBlank()) return;
        Point2D.Double p = geo.labelPoint;
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, edge.getLabelFontSize()));
        FontMetrics fm = g2.getFontMetrics();
        int sw = fm.stringWidth(edge.getLabel());
        int sh = fm.getHeight();
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Double(p.x - sw / 2.0 - 4, p.y - sh / 2.0 - 2, sw + 8, sh + 4, 8, 8));
        g2.setColor(new Color(30, 33, 38));
        g2.drawString(edge.getLabel(), (float) (p.x - sw / 2.0), (float) (p.y + fm.getAscent() / 2.0 - 2));
    }

    private EdgeGeometry edgeGeometry(DiagramEdge edge) {
        PortHit from = resolvedPort(edge, true);
        PortHit to = resolvedPort(edge, false);
        Point2D.Double s = from.point;
        Point2D.Double t = to.point;
        ArrayList<Point2D.Double> pts = new ArrayList<>();
        pts.add(s);

        boolean horizontalRoute = isHorizontalSide(from.side)
                || (!isVerticalSide(from.side) && Math.abs(t.x - s.x) >= Math.abs(t.y - s.y));

        Point2D.Double routeHandle;
        if (!orthogonal) {
            pts.add(t);
            routeHandle = new Point2D.Double((s.x + t.x) / 2, (s.y + t.y) / 2);
        } else if (horizontalRoute) {
            double midX = (s.x + t.x) / 2.0 + edge.getRouteOffset();
            Point2D.Double p1 = new Point2D.Double(midX, s.y);
            Point2D.Double p2 = new Point2D.Double(midX, t.y);
            pts.add(p1);
            if (p1.distance(p2) > 0.01) pts.add(p2);
            pts.add(t);
            routeHandle = new Point2D.Double(midX, (s.y + t.y) / 2.0);
        } else {
            double midY = (s.y + t.y) / 2.0 + edge.getRouteOffset();
            Point2D.Double p1 = new Point2D.Double(s.x, midY);
            Point2D.Double p2 = new Point2D.Double(t.x, midY);
            pts.add(p1);
            if (p1.distance(p2) > 0.01) pts.add(p2);
            pts.add(t);
            routeHandle = new Point2D.Double((s.x + t.x) / 2.0, midY);
        }

        Point2D.Double label = midpointAlongPolyline(pts);
        return new EdgeGeometry(from, to, pts, routeHandle, horizontalRoute, label);
    }

    private Point2D.Double midpointAlongPolyline(List<Point2D.Double> pts) {
        double total = 0;
        for (int i = 0; i < pts.size() - 1; i++) total += pts.get(i).distance(pts.get(i + 1));
        double target = total / 2;
        double walked = 0;
        for (int i = 0; i < pts.size() - 1; i++) {
            Point2D.Double a = pts.get(i), b = pts.get(i + 1);
            double len = a.distance(b);
            if (walked + len >= target && len > 0) {
                double r = (target - walked) / len;
                return new Point2D.Double(a.x + (b.x - a.x) * r, a.y + (b.y - a.y) * r - 8);
            }
            walked += len;
        }
        Point2D.Double last = pts.get(pts.size() - 1);
        return new Point2D.Double(last.x, last.y - 8);
    }

    private static boolean isHorizontalSide(PortSide side) {
        return side == PortSide.LEFT || side == PortSide.RIGHT;
    }
    private static boolean isVerticalSide(PortSide side) {
        return side == PortSide.TOP || side == PortSide.BOTTOM;
    }

    private PortHit resolvedPort(DiagramEdge edge, boolean from) {
        DiagramNode node = from ? edge.getFrom() : edge.getTo();
        if (node == null) return new PortHit(null, PortSide.RIGHT, 2, new Point2D.Double());
        if (from && edge.isFromPortFixed()) return portHit(node, edge.getFromSide(), edge.getFromPortIndex());
        if (!from && edge.isToPortFixed()) return portHit(node, edge.getToSide(), edge.getToPortIndex());
        DiagramNode other = from ? edge.getTo() : edge.getFrom();
        Point2D target = other == null ? new Point2D.Double(node.centerX(), node.centerY())
                : new Point2D.Double(other.centerX(), other.centerY());
        return nearestPortOnNode(node, target);
    }

    private PortHit nearestPortOnNode(DiagramNode node, Point2D target) {
        PortHit best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (PortSide side : PortSide.values()) {
            for (int i = 0; i < PORTS_PER_SIDE; i++) {
                PortHit hit = portHit(node, side, i);
                double d = hit.point.distanceSq(target);
                if (d < bestD) { bestD = d; best = hit; }
            }
        }
        return best;
    }

    private PortHit nearestPort(Point2D world, double maxWorldDistance) {
        PortHit best = null;
        double bestD = maxWorldDistance * maxWorldDistance;
        for (DiagramNode node : model.getNodes().values()) {
            for (PortSide side : PortSide.values()) {
                for (int i = 0; i < PORTS_PER_SIDE; i++) {
                    PortHit hit = portHit(node, side, i);
                    double d = hit.point.distanceSq(world);
                    if (d <= bestD) { bestD = d; best = hit; }
                }
            }
        }
        return best;
    }

    private PortHit portHit(DiagramNode node, PortSide side, int index) {
        double f = (Math.max(0, Math.min(4, index)) + 1) / 6.0;
        double x, y;
        if (node.getShapeType() == ShapeType.DIAMOND) {
            double leftX = node.getX(), rightX = node.getX() + node.getWidth();
            double topY = node.getY(), bottomY = node.getY() + node.getHeight();
            double cx = node.centerX(), cy = node.centerY();
            switch (side) {
                case TOP -> { x = leftX + (cx - leftX) * f; y = cy + (topY - cy) * f; }
                case RIGHT -> { x = cx + (rightX - cx) * f; y = topY + (cy - topY) * f; }
                case BOTTOM -> { x = rightX + (cx - rightX) * f; y = cy + (bottomY - cy) * f; }
                case LEFT -> { x = cx + (leftX - cx) * f; y = bottomY + (cy - bottomY) * f; }
                default -> throw new IllegalStateException();
            }
        } else {
            switch (side) {
                case TOP -> { x = node.getX() + node.getWidth() * f; y = node.getY(); }
                case RIGHT -> { x = node.getX() + node.getWidth(); y = node.getY() + node.getHeight() * f; }
                case BOTTOM -> { x = node.getX() + node.getWidth() * (1 - f); y = node.getY() + node.getHeight(); }
                case LEFT -> { x = node.getX(); y = node.getY() + node.getHeight() * (1 - f); }
                default -> throw new IllegalStateException();
            }
        }
        return new PortHit(node, side, index, new Point2D.Double(x, y));
    }

    private void paintPorts(Graphics2D g2, DiagramNode node, boolean allCandidateMode) {
        double r = 3.5 / zoom;
        for (PortSide side : PortSide.values()) {
            for (int i = 0; i < PORTS_PER_SIDE; i++) {
                PortHit p = portHit(node, side, i);
                boolean hot = snapCandidate != null && snapCandidate.samePort(p);
                g2.setColor(hot ? new Color(20, 160, 90) : new Color(70, 130, 230));
                double rr = hot ? r * 1.7 : r;
                Ellipse2D dot = new Ellipse2D.Double(p.point.x - rr, p.point.y - rr, rr * 2, rr * 2);
                g2.fill(dot);
                if (!allCandidateMode) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke((float) (1.0 / zoom)));
                    g2.draw(dot);
                }
            }
        }
    }

    private void paintEdgeHandles(Graphics2D g2, DiagramEdge edge) {
        EdgeGeometry geo = edgeGeometry(edge);
        double r = 6 / zoom;
        g2.setStroke(new BasicStroke((float) (1.2 / zoom)));
        paintHandleCircle(g2, geo.from.point, r, new Color(30, 125, 245));
        paintHandleCircle(g2, geo.to.point, r, new Color(30, 125, 245));
        if (orthogonal) {
            double s = 11 / zoom;
            g2.setColor(new Color(255, 170, 25));
            g2.fill(new Rectangle2D.Double(geo.routeHandle.x - s / 2, geo.routeHandle.y - s / 2, s, s));
            g2.setColor(Color.WHITE);
            g2.draw(new Rectangle2D.Double(geo.routeHandle.x - s / 2, geo.routeHandle.y - s / 2, s, s));
        }
    }

    private void paintHandleCircle(Graphics2D g2, Point2D p, double r, Color color) {
        g2.setColor(color);
        g2.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, r * 2, r * 2));
        g2.setColor(Color.WHITE);
        g2.draw(new Ellipse2D.Double(p.getX() - r, p.getY() - r, r * 2, r * 2));
    }

    private void paintResizeHandles(Graphics2D g2, DiagramNode n) {
        double s = HANDLE_PIXELS / zoom;
        g2.setColor(new Color(40, 115, 230));
        for (Point2D.Double p : resizeHandlePoints(n).values()) {
            g2.fill(new Rectangle2D.Double(p.x - s / 2, p.y - s / 2, s, s));
        }
    }

    private Map<ResizeHandle, Point2D.Double> resizeHandlePoints(DiagramNode n) {
        double x = n.getX(), y = n.getY(), r = x + n.getWidth(), b = y + n.getHeight();
        double cx = n.centerX(), cy = n.centerY();
        EnumMap<ResizeHandle, Point2D.Double> m = new EnumMap<>(ResizeHandle.class);
        m.put(ResizeHandle.NW, new Point2D.Double(x, y));
        m.put(ResizeHandle.N, new Point2D.Double(cx, y));
        m.put(ResizeHandle.NE, new Point2D.Double(r, y));
        m.put(ResizeHandle.E, new Point2D.Double(r, cy));
        m.put(ResizeHandle.SE, new Point2D.Double(r, b));
        m.put(ResizeHandle.S, new Point2D.Double(cx, b));
        m.put(ResizeHandle.SW, new Point2D.Double(x, b));
        m.put(ResizeHandle.W, new Point2D.Double(x, cy));
        return m;
    }

    private void paintFloatingEdge(Graphics2D g2) {
        if (!(selection instanceof DiagramEdge edge) || floatingEndpoint == null) return;
        EdgeGeometry geo = edgeGeometry(edge);
        Point2D.Double fixed = dragMode == DragMode.EDGE_FROM ? geo.to.point : geo.from.point;
        Point2D.Double moving = snapCandidate != null ? snapCandidate.point : floatingEndpoint;
        g2.setColor(new Color(30, 125, 245));
        g2.setStroke(new BasicStroke((float) (1.5 / zoom), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, new float[]{6f / (float) zoom, 4f / (float) zoom}, 0));
        g2.draw(new Line2D.Double(fixed, moving));
    }

    private void onMousePressed(MouseEvent e) {
        requestFocusInWindow();
        Point2D.Double world = screenToWorld(e.getPoint());
        dragScreenStart = e.getPoint();
        dragWorldStart = world;

        if (selection instanceof DiagramEdge edge) {
            EdgeGeometry geo = edgeGeometry(edge);
            double tol = 10 / zoom;
            if (geo.from.point.distance(world) <= tol) {
                dragMode = DragMode.EDGE_FROM; dragEdge = edge; floatingEndpoint = world; return;
            }
            if (geo.to.point.distance(world) <= tol) {
                dragMode = DragMode.EDGE_TO; dragEdge = edge; floatingEndpoint = world; return;
            }
            if (orthogonal && geo.routeHandle.distance(world) <= tol) {
                dragMode = DragMode.EDGE_ROUTE; dragEdge = edge; routeOffsetStart = edge.getRouteOffset(); return;
            }
        }

        if (selection instanceof DiagramNode selectedNode) {
            ResizeHandle h = hitResizeHandle(selectedNode, world);
            if (h != null) {
                dragMode = DragMode.RESIZE;
                dragNode = selectedNode;
                resizeHandle = h;
                rememberNodeBounds(selectedNode);
                return;
            }
        }

        DiagramNode node = hitNode(world);
        if (node != null) {
            setSelection(node);
            dragMode = DragMode.NODE;
            dragNode = node;
            rememberNodeBounds(node);
            setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            return;
        }

        DiagramEdge edge = hitEdge(world);
        if (edge != null) {
            setSelection(edge);
            dragMode = DragMode.NONE;
            return;
        }

        setSelection(null);
        dragMode = DragMode.PAN;
        startPanX = panX;
        startPanY = panY;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void rememberNodeBounds(DiagramNode node) {
        nodeStartX = node.getX(); nodeStartY = node.getY();
        nodeStartW = node.getWidth(); nodeStartH = node.getHeight();
    }

    private void onMouseDragged(MouseEvent e) {
        if (dragWorldStart == null) return;
        Point2D.Double world = screenToWorld(e.getPoint());
        double dx = world.x - dragWorldStart.x;
        double dy = world.y - dragWorldStart.y;

        switch (dragMode) {
            case PAN -> {
                panX = startPanX + (e.getX() - dragScreenStart.x);
                panY = startPanY + (e.getY() - dragScreenStart.y);
            }
            case NODE -> {
                dragNode.setX(nodeStartX + dx);
                dragNode.setY(nodeStartY + dy);
            }
            case RESIZE -> resizeNode(dx, dy);
            case EDGE_FROM, EDGE_TO -> {
                floatingEndpoint = world;
                snapCandidate = nearestPort(world, SNAP_PIXELS / zoom);
            }
            case EDGE_ROUTE -> {
                EdgeGeometry geo = edgeGeometry(dragEdge);
                dragEdge.setRouteOffset(routeOffsetStart + (geo.horizontalRoute ? dx : dy));
            }
            default -> { }
        }
        repaint();
        if (selectionListener != null && (dragMode == DragMode.NODE || dragMode == DragMode.RESIZE || dragMode == DragMode.EDGE_ROUTE)) {
            selectionListener.selectionChanged(selection);
        }
    }

    private void onMouseReleased(MouseEvent e) {
        if ((dragMode == DragMode.EDGE_FROM || dragMode == DragMode.EDGE_TO) && dragEdge != null && snapCandidate != null) {
            if (dragMode == DragMode.EDGE_FROM) {
                dragEdge.attachFrom(snapCandidate.node, snapCandidate.side, snapCandidate.index);
            } else {
                dragEdge.attachTo(snapCandidate.node, snapCandidate.side, snapCandidate.index);
            }
        }
        dragMode = DragMode.NONE;
        dragNode = null;
        dragEdge = null;
        resizeHandle = null;
        floatingEndpoint = null;
        snapCandidate = null;
        dragWorldStart = null;
        setCursor(Cursor.getDefaultCursor());
        repaint();
        if (selectionListener != null) selectionListener.selectionChanged(selection);
    }

    private void onMouseMoved(MouseEvent e) {
        Point2D.Double world = screenToWorld(e.getPoint());
        if (selection instanceof DiagramEdge edge) {
            EdgeGeometry geo = edgeGeometry(edge);
            double tol = 10 / zoom;
            if (geo.from.point.distance(world) <= tol || geo.to.point.distance(world) <= tol) {
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)); return;
            }
            if (orthogonal && geo.routeHandle.distance(world) <= tol) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)); return;
            }
        }
        if (selection instanceof DiagramNode node) {
            ResizeHandle h = hitResizeHandle(node, world);
            if (h != null) { setCursor(cursorForResize(h)); return; }
        }
        if (hitNode(world) != null) setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        else if (hitEdge(world) != null) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        else setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void onMouseWheel(MouseWheelEvent e) {
        double factor = Math.pow(1.12, -e.getPreciseWheelRotation());
        zoomAt(e.getPoint(), zoom * factor);
    }

    private void zoomAt(Point anchor, double newZoom) {
        newZoom = clampZoom(newZoom);
        double wx = (anchor.x - panX) / zoom;
        double wy = (anchor.y - panY) / zoom;
        zoom = newZoom;
        panX = anchor.x - wx * zoom;
        panY = anchor.y - wy * zoom;
        repaint();
    }

    private double clampZoom(double z) { return Math.max(0.12, Math.min(5.0, z)); }

    private Point2D.Double screenToWorld(Point p) {
        return new Point2D.Double((p.x - panX) / zoom, (p.y - panY) / zoom);
    }

    private DiagramNode hitNode(Point2D p) {
        List<DiagramNode> nodes = new ArrayList<>(model.getNodes().values());
        Collections.reverse(nodes);
        for (DiagramNode n : nodes) if (nodeShape(n).contains(p)) return n;
        return null;
    }

    private DiagramEdge hitEdge(Point2D p) {
        List<DiagramEdge> edges = new ArrayList<>(model.getEdges());
        Collections.reverse(edges);
        double tol = 8 / zoom;
        for (DiagramEdge e : edges) {
            List<Point2D.Double> pts = edgeGeometry(e).points;
            for (int i = 0; i < pts.size() - 1; i++) {
                if (Line2D.ptSegDist(pts.get(i).x, pts.get(i).y, pts.get(i + 1).x, pts.get(i + 1).y,
                        p.getX(), p.getY()) <= tol) return e;
            }
        }
        return null;
    }

    private ResizeHandle hitResizeHandle(DiagramNode n, Point2D p) {
        double tol = 8 / zoom;
        for (Map.Entry<ResizeHandle, Point2D.Double> e : resizeHandlePoints(n).entrySet()) {
            if (e.getValue().distance(p) <= tol) return e.getKey();
        }
        return null;
    }

    private Cursor cursorForResize(ResizeHandle h) {
        return switch (h) {
            case N, S -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case E, W -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case NW, SE -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case NE, SW -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
        };
    }

    private void resizeNode(double dx, double dy) {
        if (dragNode == null || resizeHandle == null) return;
        double x = nodeStartX, y = nodeStartY, w = nodeStartW, h = nodeStartH;
        boolean left = resizeHandle == ResizeHandle.NW || resizeHandle == ResizeHandle.W || resizeHandle == ResizeHandle.SW;
        boolean right = resizeHandle == ResizeHandle.NE || resizeHandle == ResizeHandle.E || resizeHandle == ResizeHandle.SE;
        boolean top = resizeHandle == ResizeHandle.NW || resizeHandle == ResizeHandle.N || resizeHandle == ResizeHandle.NE;
        boolean bottom = resizeHandle == ResizeHandle.SW || resizeHandle == ResizeHandle.S || resizeHandle == ResizeHandle.SE;

        if (left) { x = nodeStartX + dx; w = nodeStartW - dx; }
        if (right) w = nodeStartW + dx;
        if (top) { y = nodeStartY + dy; h = nodeStartH - dy; }
        if (bottom) h = nodeStartH + dy;

        if (w < MIN_NODE_W) {
            if (left) x -= (MIN_NODE_W - w);
            w = MIN_NODE_W;
        }
        if (h < MIN_NODE_H) {
            if (top) y -= (MIN_NODE_H - h);
            h = MIN_NODE_H;
        }
        dragNode.setX(x); dragNode.setY(y); dragNode.setWidth(w); dragNode.setHeight(h);
    }

    private void setSelection(Object value) {
        if (selection == value) return;
        selection = value;
        if (selectionListener != null) selectionListener.selectionChanged(value);
        repaint();
    }

    public void deleteSelection() {
        if (selection instanceof DiagramNode node) model.removeNode(node);
        else if (selection instanceof DiagramEdge edge) model.removeEdge(edge);
        setSelection(null);
        repaint();
    }

    private Rectangle2D modelBounds() {
        if (model.getNodes().isEmpty()) return null;
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (DiagramNode n : model.getNodes().values()) {
            minX = Math.min(minX, n.getX()); minY = Math.min(minY, n.getY());
            maxX = Math.max(maxX, n.getX() + n.getWidth()); maxY = Math.max(maxY, n.getY() + n.getHeight());
        }
        for (DiagramEdge e : model.getEdges()) {
            for (Point2D p : edgeGeometry(e).points) {
                minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY());
                maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY());
            }
        }
        return new Rectangle2D.Double(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private record PortHit(DiagramNode node, PortSide side, int index, Point2D.Double point) {
        boolean samePort(PortHit other) {
            return other != null && node == other.node && side == other.side && index == other.index;
        }
    }

    private record EdgeGeometry(PortHit from, PortHit to, List<Point2D.Double> points,
                                Point2D.Double routeHandle, boolean horizontalRoute,
                                Point2D.Double labelPoint) {}
}
