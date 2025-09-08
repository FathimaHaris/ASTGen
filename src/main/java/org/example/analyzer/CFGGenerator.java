package org.example.analyzer;

import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.graph.StmtGraph;
import sootup.core.model.SootMethod;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.views.JavaView;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.*;
import java.util.stream.Collectors;

public class CFGGenerator {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar cfg-generator.jar <config.xml>");
            return;
        }

        try {
            // Load configuration
            Map<String, String> config = loadConfig(args[0]);

            // Generate CFG
            generateCFG(config);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> loadConfig(String configPath) throws Exception {
        Map<String, String> config = new HashMap<>();
        var doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(configPath);

        doc.getDocumentElement().normalize();

        config.put("classPath", doc.getElementsByTagName("classPath").item(0).getTextContent());
        config.put("className", doc.getElementsByTagName("className").item(0).getTextContent());
        config.put("methodName", doc.getElementsByTagName("methodName").item(0).getTextContent());

        return config;
    }

    public static void generateCFG(Map<String, String> config) {
        // Setup analysis input location
        AnalysisInputLocation inputLocation =
                new JavaClassPathAnalysisInputLocation(config.get("classPath"));

        JavaView view = new JavaView(Collections.singletonList(inputLocation));

        // Find the target class and method
        var identifierFactory = view.getIdentifierFactory();
        var classType = identifierFactory.getClassType(config.get("className"));

        view.getClass(classType).ifPresent(sootClass -> {
            sootClass.getMethods().stream()
                    .filter(method -> method.getName().equals(config.get("methodName")))
                    .findFirst()
                    .ifPresent(method -> {
                        System.out.println("=== CFG for Method: " + method.getName() + " ===");
                        generateAndDisplayCFG(method);
                    });
        });
    }

    public static void generateAndDisplayCFG(SootMethod method) {
        // Get the control flow graph
        StmtGraph<?> cfg = method.getBody().getStmtGraph();

        System.out.println("\n🔷 Basic Blocks and Control Flow:");
        System.out.println("==================================");

        // Print CFG in text format
        printTextCFG(cfg);

        // Generate DOT format for visualization
        System.out.println("\n🔷 DOT Format (for Graphviz):");
        System.out.println("==============================");
        String dotFormat = generateDOTCFG(cfg, method.getName());
        System.out.println(dotFormat);

        // Additional analysis
        analyzeCFG(cfg);
    }

    public static void printTextCFG(StmtGraph<?> cfg) {
        Map<Stmt, Integer> stmtToId = new HashMap<>();
        int nodeId = 1;

        // Assign IDs to statements
        for (Stmt stmt : cfg.getNodes()) {
            stmtToId.put(stmt, nodeId++);
        }

        // Print nodes
        for (Map.Entry<Stmt, Integer> entry : stmtToId.entrySet()) {
            System.out.println("Node " + entry.getValue() + ": " +
                    entry.getKey().toString().replace("\n", " "));
        }

        // Print edges
        System.out.println("\nControl Flow Edges:");
        for (Stmt source : cfg.getNodes()) {
            for (Stmt target : cfg.successors(source)) {
                System.out.println("  " + stmtToId.get(source) + " → " + stmtToId.get(target));
            }
        }
    }

    public static String generateDOTCFG(StmtGraph<?> cfg, String methodName) {
        StringBuilder dot = new StringBuilder();
        Map<Stmt, Integer> stmtToId = new HashMap<>();
        int nodeId = 1;

        dot.append("digraph CFG_").append(methodName).append(" {\n");
        dot.append("  rankdir=TB;\n  node [shape=rectangle, style=filled, fillcolor=lightblue];\n\n");

        // Create nodes
        for (Stmt stmt : cfg.getNodes()) {
            stmtToId.put(stmt, nodeId);
            String label = stmt.toString().replace("\"", "\\\"")
                    .replace("\n", "\\l");
            dot.append("  node").append(nodeId)
                    .append(" [label=\"").append(label).append("\"];\n");
            nodeId++;
        }

        dot.append("\n");

        // Create edges
        for (Stmt source : cfg.getNodes()) {
            for (Stmt target : cfg.successors(source)) {
                dot.append("  node").append(stmtToId.get(source))
                        .append(" -> node").append(stmtToId.get(target))
                        .append(";\n");
            }
        }

        dot.append("}");
        return dot.toString();
    }

    public static void analyzeCFG(StmtGraph<?> cfg) {
        System.out.println("\n📊 CFG Analysis:");
        System.out.println("================");
        System.out.println("Total Nodes: " + cfg.getNodes().size());

        int edgeCount = 0;
        for (Stmt node : cfg.getNodes()) {
            edgeCount += cfg.successors(node).size();
        }
        System.out.println("Total Edges: " + edgeCount);

        // CORRECTED: getStartingStmt() returns Optional, so we handle it properly
        Stmt  entryStmt = cfg.getStartingStmt();

        if (entryStmt != null) {
            System.out.println("Entry Point: " + entryStmt.toString().replace("\n", " "));
        } else {
            System.out.println("No explicit entry point found");
        }

        // Additional analysis: count branch statements
        long branchCount = cfg.getNodes().stream()
                .filter(stmt -> stmt.toString().contains("if") ||
                        stmt.toString().contains("goto"))
                .count();
        System.out.println("Branch Statements: " + branchCount);
    }
}