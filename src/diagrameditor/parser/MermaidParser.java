package diagrameditor.parser;

import diagrameditor.model.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MermaidParser {
    private static final Pattern RECT = Pattern.compile("^([A-Za-z0-9_:.\\-]+)\\[(.*)]$");
    private static final Pattern ROUND = Pattern.compile("^([A-Za-z0-9_:.\\-]+)\\((.*)\\)$");
    private static final Pattern DIAMOND = Pattern.compile("^([A-Za-z0-9_:.\\-]+)\\{(.*)}$");
    private static final Pattern BARE = Pattern.compile("^([A-Za-z0-9_:.\\-]+)$");
    private static final Pattern CLASS_DECL = Pattern.compile("^class\\s+([A-Za-z0-9_:.\\-]+)(?:\\s*\\{)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECTION = Pattern.compile("^direction\\s+(LR|RL|TB|TD|BT)$", Pattern.CASE_INSENSITIVE);

    private MermaidParser() {}

    public static DiagramModel parse(String source) {
        List<String> rawLines = source.lines().toList();
        String first = rawLines.stream().map(String::trim).filter(s -> !s.isBlank() && !s.startsWith("%%")).findFirst().orElse("");
        if (first.toLowerCase(Locale.ROOT).startsWith("classdiagram")) {
            return parseClassDiagram(rawLines);
        }
        return parseFlowchart(rawLines);
    }

    public static void relayout(DiagramModel model, String direction) {
        if (model == null) return;
        String dir = normalizeDirection(direction);
        model.setDirection(dir);
        autoLayout(model);
    }

    private static DiagramModel parseFlowchart(List<String> rawLines) {
        DiagramModel model = new DiagramModel();
        model.setDiagramType(DiagramType.FLOWCHART);
        model.setDirection("LR");

        List<String> lines = rawLines.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .filter(s -> !s.startsWith("%%"))
                .toList();

        int start = 0;
        if (!lines.isEmpty() && lines.get(0).toLowerCase(Locale.ROOT).startsWith("flowchart")) {
            String[] parts = lines.get(0).split("\\s+");
            if (parts.length > 1) model.setDirection(normalizeDirection(parts[1]));
            start = 1;
        }

        for (int i = start; i < lines.size(); i++) {
            parseFlowLine(lines.get(i), model, i + 1);
        }

        autoLayout(model);
        return model;
    }

    private static void parseFlowLine(String line, DiagramModel model, int lineNumber) {
        int directedIndex = line.indexOf("-->");
        if (directedIndex >= 0) {
            String left = line.substring(0, directedIndex).trim();
            String rest = line.substring(directedIndex + 3).trim();
            String label = "";
            if (rest.startsWith("|")) {
                int end = rest.indexOf('|', 1);
                if (end < 0) throw new IllegalArgumentException("Line " + lineNumber + ": edge label is not closed.");
                label = rest.substring(1, end).trim();
                rest = rest.substring(end + 1).trim();
            }
            DiagramNode from = ensureFlowNode(parseFlowNode(left, lineNumber), model);
            DiagramNode to = ensureFlowNode(parseFlowNode(rest, lineNumber), model);
            model.addEdge(new DiagramEdge(from, to, label, EdgeType.FLOW));
            return;
        }

        int undirectedIndex = line.indexOf("---");
        if (undirectedIndex >= 0) {
            String left = line.substring(0, undirectedIndex).trim();
            String right = line.substring(undirectedIndex + 3).trim();
            DiagramNode from = ensureFlowNode(parseFlowNode(left, lineNumber), model);
            DiagramNode to = ensureFlowNode(parseFlowNode(right, lineNumber), model);
            model.addEdge(new DiagramEdge(from, to, "", EdgeType.ASSOCIATION));
            return;
        }

        ensureFlowNode(parseFlowNode(line, lineNumber), model);
    }

    private static DiagramNode parseFlowNode(String token, int lineNumber) {
        token = token.trim();
        Matcher m = RECT.matcher(token);
        if (m.matches()) return new DiagramNode(m.group(1), m.group(2), ShapeType.RECTANGLE);
        m = ROUND.matcher(token);
        if (m.matches()) return new DiagramNode(m.group(1), m.group(2), ShapeType.ROUNDED);
        m = DIAMOND.matcher(token);
        if (m.matches()) return new DiagramNode(m.group(1), m.group(2), ShapeType.DIAMOND);
        m = BARE.matcher(token);
        if (m.matches()) return new DiagramNode(m.group(1), m.group(1), ShapeType.RECTANGLE);
        throw new IllegalArgumentException("Line " + lineNumber + ": unsupported node expression: " + token);
    }

    private static DiagramNode ensureFlowNode(DiagramNode candidate, DiagramModel model) {
        DiagramNode existing = model.getNode(candidate.getId());
        if (existing == null) {
            model.addNode(candidate);
            return candidate;
        }
        boolean explicit = !candidate.getText().equals(candidate.getId()) || candidate.getShapeType() != ShapeType.RECTANGLE;
        if (explicit) {
            existing.setText(candidate.getText());
            existing.setShapeType(candidate.getShapeType());
            if (candidate.getShapeType() == ShapeType.DIAMOND) {
                existing.setWidth(140);
                existing.setHeight(90);
            }
        }
        return existing;
    }

    private static DiagramModel parseClassDiagram(List<String> rawLines) {
        DiagramModel model = new DiagramModel();
        model.setDiagramType(DiagramType.CLASS_DIAGRAM);
        model.setDirection("TB");

        String currentClass = null;
        boolean inBlock = false;
        int logicalLine = 0;

        for (String raw : rawLines) {
            logicalLine++;
            String line = raw.trim();
            if (line.isBlank() || line.startsWith("%%")) continue;
            if (line.equalsIgnoreCase("classDiagram")) continue;

            Matcher directionMatcher = DIRECTION.matcher(line);
            if (!inBlock && directionMatcher.matches()) {
                model.setDirection(normalizeDirection(directionMatcher.group(1)));
                continue;
            }

            if (inBlock) {
                if (line.equals("}")) {
                    inBlock = false;
                    currentClass = null;
                    continue;
                }
                DiagramNode node = model.getNode(currentClass);
                if (node == null) throw new IllegalArgumentException("Line " + logicalLine + ": class block lost its class declaration.");
                addClassMember(node, line);
                continue;
            }

            Matcher classMatcher = CLASS_DECL.matcher(line);
            if (classMatcher.matches()) {
                String className = classMatcher.group(1);
                DiagramNode node = ensureClassNode(model, className);
                if (line.endsWith("{")) {
                    inBlock = true;
                    currentClass = node.getId();
                }
                continue;
            }

            if (line.startsWith("class ") && line.contains("{")) {
                int brace = line.indexOf('{');
                String declaration = line.substring(0, brace).trim();
                Matcher inlineClass = CLASS_DECL.matcher(declaration + " {");
                if (!inlineClass.matches()) throw new IllegalArgumentException("Line " + logicalLine + ": invalid class declaration: " + line);
                DiagramNode node = ensureClassNode(model, inlineClass.group(1));
                String remainder = line.substring(brace + 1).trim();
                if (remainder.endsWith("}")) {
                    remainder = remainder.substring(0, remainder.length() - 1).trim();
                    if (!remainder.isBlank()) addClassMember(node, remainder);
                } else {
                    inBlock = true;
                    currentClass = node.getId();
                    if (!remainder.isBlank()) addClassMember(node, remainder);
                }
                continue;
            }

            if (parseClassRelation(line, model, logicalLine)) continue;

            throw new IllegalArgumentException("Line " + logicalLine + ": unsupported classDiagram expression: " + line);
        }

        if (inBlock) throw new IllegalArgumentException("classDiagram: class block is not closed with '}'.");
        autoLayout(model);
        return model;
    }

    private static void addClassMember(DiagramNode node, String member) {
        String value = member.trim();
        if (value.isBlank()) return;
        if (value.contains("(") && value.contains(")")) node.addMethod(value);
        else node.addAttribute(value);
    }

    private static boolean parseClassRelation(String line, DiagramModel model, int lineNumber) {
        String[] connectors = {"<|--", "--|>", "*--", "--*", "o--", "--o", "..|>", "<|..", "..>", "<..", "-->", "<--", "--"};
        for (String connector : connectors) {
            int idx = line.indexOf(connector);
            if (idx < 0) continue;

            String left = line.substring(0, idx).trim();
            String rightAndLabel = line.substring(idx + connector.length()).trim();
            String right = rightAndLabel;
            String label = "";
            int colon = rightAndLabel.indexOf(':');
            if (colon >= 0) {
                right = rightAndLabel.substring(0, colon).trim();
                label = rightAndLabel.substring(colon + 1).trim();
            }

            left = stripMultiplicity(left);
            right = stripMultiplicity(right);
            if (!BARE.matcher(left).matches() || !BARE.matcher(right).matches()) {
                throw new IllegalArgumentException("Line " + lineNumber + ": unsupported class relation: " + line);
            }

            DiagramNode leftNode = ensureClassNode(model, left);
            DiagramNode rightNode = ensureClassNode(model, right);
            switch (connector) {
                case "<|--" -> model.addEdge(new DiagramEdge(rightNode, leftNode, label, EdgeType.INHERITANCE));
                case "--|>" -> model.addEdge(new DiagramEdge(leftNode, rightNode, label, EdgeType.INHERITANCE));
                case "*--" -> model.addEdge(new DiagramEdge(leftNode, rightNode, label, EdgeType.COMPOSITION));
                case "--*" -> model.addEdge(new DiagramEdge(rightNode, leftNode, label, EdgeType.COMPOSITION));
                case "o--" -> model.addEdge(new DiagramEdge(leftNode, rightNode, label, EdgeType.AGGREGATION));
                case "--o" -> model.addEdge(new DiagramEdge(rightNode, leftNode, label, EdgeType.AGGREGATION));
                case "..|>" -> model.addEdge(new DiagramEdge(leftNode, rightNode, label, EdgeType.REALIZATION));
                case "<|.." -> model.addEdge(new DiagramEdge(rightNode, leftNode, label, EdgeType.REALIZATION));
                case "..>" -> model.addEdge(new DiagramEdge(leftNode, rightNode, label, EdgeType.DEPENDENCY));
                case "<.." -> model.addEdge(new DiagramEdge(rightNode, leftNode, label, EdgeType.DEPENDENCY));
                case "-->" -> model.addEdge(new DiagramEdge(leftNode, rightNode, label, EdgeType.DIRECTED_ASSOCIATION));
                case "<--" -> model.addEdge(new DiagramEdge(rightNode, leftNode, label, EdgeType.DIRECTED_ASSOCIATION));
                case "--" -> model.addEdge(new DiagramEdge(leftNode, rightNode, label, EdgeType.ASSOCIATION));
                default -> throw new IllegalStateException("Unexpected connector: " + connector);
            }
            return true;
        }
        return false;
    }

    private static String stripMultiplicity(String token) {
        String cleaned = token.trim();
        cleaned = cleaned.replaceAll("^\\\"[^\\\"]*\\\"\\s*", "");
        cleaned = cleaned.replaceAll("\\s*\\\"[^\\\"]*\\\"$", "");
        return cleaned.trim();
    }

    private static DiagramNode ensureClassNode(DiagramModel model, String id) {
        DiagramNode existing = model.getNode(id);
        if (existing != null) return existing;
        DiagramNode node = new DiagramNode(id, id, ShapeType.CLASS_BOX);
        model.addNode(node);
        return node;
    }

    private static String normalizeDirection(String direction) {
        String dir = direction == null ? "LR" : direction.toUpperCase(Locale.ROOT);
        if (dir.equals("TD")) dir = "TB";
        if (!Set.of("LR", "RL", "TB", "BT").contains(dir)) {
            throw new IllegalArgumentException("Unsupported layout direction: " + direction);
        }
        return dir;
    }

    private static void autoLayout(DiagramModel model) {
        List<DiagramNode> nodes = new ArrayList<>(model.getNodes().values());
        if (nodes.isEmpty()) return;

        Map<DiagramNode, Integer> indegree = new LinkedHashMap<>();
        Map<DiagramNode, List<DiagramNode>> next = new LinkedHashMap<>();
        for (DiagramNode node : nodes) {
            indegree.put(node, 0);
            next.put(node, new ArrayList<>());
        }
        for (DiagramEdge edge : model.getEdges()) {
            if (edge.getFrom() == edge.getTo()) continue;
            indegree.computeIfPresent(edge.getTo(), (k, v) -> v + 1);
            next.computeIfPresent(edge.getFrom(), (k, v) -> {
                v.add(edge.getTo());
                return v;
            });
        }

        ArrayDeque<LevelNode> queue = new ArrayDeque<>();
        nodes.stream().filter(n -> indegree.getOrDefault(n, 0) == 0).forEach(n -> queue.add(new LevelNode(n, 0)));
        if (queue.isEmpty()) queue.add(new LevelNode(nodes.get(0), 0));

        Map<DiagramNode, Integer> levels = new LinkedHashMap<>();
        while (!queue.isEmpty()) {
            LevelNode current = queue.removeFirst();
            int previous = levels.getOrDefault(current.node(), -1);
            if (current.level() <= previous) continue;
            levels.put(current.node(), current.level());
            if (current.level() > nodes.size()) continue;
            for (DiagramNode child : next.getOrDefault(current.node(), List.of())) {
                queue.addLast(new LevelNode(child, current.level() + 1));
            }
        }
        for (DiagramNode node : nodes) levels.putIfAbsent(node, 0);

        Map<Integer, List<DiagramNode>> groups = new TreeMap<>();
        for (DiagramNode node : nodes) groups.computeIfAbsent(levels.get(node), k -> new ArrayList<>()).add(node);

        double marginX = 90, marginY = 80;
        double gapX = model.getDiagramType() == DiagramType.CLASS_DIAGRAM ? 310 : 240;
        double gapY = model.getDiagramType() == DiagramType.CLASS_DIAGRAM ? 220 : 150;
        boolean horizontal = model.getDirection().equals("LR") || model.getDirection().equals("RL");

        for (Map.Entry<Integer, List<DiagramNode>> entry : groups.entrySet()) {
            int level = entry.getKey();
            List<DiagramNode> group = entry.getValue();
            for (int i = 0; i < group.size(); i++) {
                DiagramNode node = group.get(i);
                if (horizontal) {
                    node.setX(marginX + level * gapX);
                    node.setY(marginY + i * gapY);
                } else {
                    node.setX(marginX + i * gapX);
                    node.setY(marginY + level * gapY);
                }
            }
        }

        if (model.getDirection().equals("RL")) {
            double max = nodes.stream().mapToDouble(DiagramNode::getX).max().orElse(0);
            for (DiagramNode n : nodes) n.setX(max - n.getX() + marginX);
        } else if (model.getDirection().equals("BT")) {
            double max = nodes.stream().mapToDouble(DiagramNode::getY).max().orElse(0);
            for (DiagramNode n : nodes) n.setY(max - n.getY() + marginY);
        }
    }

    private record LevelNode(DiagramNode node, int level) {}
}
