package org.example.analyzer;

import sootup.core.graph.StmtGraph;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CFG {

    public static void main(String[] args) throws IOException {
        String classPath = (args.length > 0) ? args[0] : "target/classes";
        String packagePrefix = (args.length > 1) ? args[1] : "org.example"; // scan everything under org.example

        List<AnalysisInputLocation> inputLocations = List.of(
                new JavaClassPathAnalysisInputLocation(classPath));
        JavaView view = new JavaView(inputLocations);

        List<String> classNames = findClasses(classPath, packagePrefix);

        if (classNames.isEmpty()) {
            System.err.println("No classes found under package: " + packagePrefix);
            return;
        }

        System.out.println("Discovered classes:");
        classNames.forEach(c -> System.out.println("  • " + c));

        Path outRoot = Paths.get("target/cfg");
        Files.createDirectories(outRoot);

        for (String className : classNames) {
            Optional<JavaSootClass> opt = view.getClass(view.getIdentifierFactory().getClassType(className));
            if (opt.isEmpty()) {
                System.err.println("Class not found in view: " + className);
                continue;
            }
            JavaSootClass sc = opt.get();
            System.out.println("\n================ CLASS: " + className + " ================");

            for (JavaSootMethod m : sc.getMethods()) {
                System.out.println("\n--- METHOD: " + m.getName() + " ---");
                if (!m.hasBody()) {
                    System.out.println("  <no body>");
                    continue;
                }
                var cfg = m.getBody().getStmtGraph();

                // Print CFG in text format
                printTextCFG(cfg);

                // Also generate DOT format
                String dot = generateDOTCFG(cfg, m.getName());

                // Write to file: target/cfg/<className>/<methodName>.dot
                String safeClass = className.replace('.', '_');
                String safeMethod = m.getName().replaceAll("[^a-zA-Z0-9_]", "_");
                Path classDir = outRoot.resolve(safeClass);
                Files.createDirectories(classDir);
                Path outFile = classDir.resolve(safeMethod + ".dot");
                try {
                    Files.writeString(outFile, dot, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    System.out.println("  -> Wrote CFG to: " + outFile.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("  ! Failed to write CFG: " + e.getMessage());
                }
            }
        }
    }

    private static List<String> findClasses(String classesRoot, String packagePrefix) throws IOException {
        Path root = Paths.get(classesRoot);
        if (!Files.exists(root)) return List.of();

        String pkgPath = packagePrefix.replace('.', '/');
        Path start = root.resolve(pkgPath);
        if (!Files.exists(start)) return List.of();

        try (Stream<Path> stream = Files.walk(start)) {
            return stream
                    .filter(p -> p.toString().endsWith(".class"))
                    .filter(p -> !p.getFileName().toString().contains("$")) // skip inner classes
                    .map(root::relativize)
                    .map(p -> p.toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
                    .collect(Collectors.toList());
        }
    }

    private static void printTextCFG(StmtGraph<?> cfg) {
        Map<Stmt, Integer> stmtToId = new HashMap<>();
        int nodeId = 1;

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

    private static String generateDOTCFG(StmtGraph<?> cfg, String methodName) {
        StringBuilder dot = new StringBuilder();
        Map<Stmt, Integer> stmtToId = new HashMap<>();
        int nodeId = 1;

        dot.append("digraph CFG_").append(methodName).append(" {\n");
        dot.append("  rankdir=TB;\n  node [shape=rectangle, style=filled, fillcolor=lightblue];\n\n");

        for (Stmt stmt : cfg.getNodes()) {
            stmtToId.put(stmt, nodeId);
            String label = stmt.toString().replace("\"", "\\\"").replace("\n", "\\l");
            dot.append("  node").append(nodeId)
                    .append(" [label=\"").append(label).append("\"];\n");
            nodeId++;
        }

        dot.append("\n");

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
}
