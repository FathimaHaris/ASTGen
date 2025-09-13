package org.example.analyzer;

import org.example.analyzer.dependency.*;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.core.types.ClassType;
import sootup.core.model.Body;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        String classPath = (args.length > 0) ? args[0] : "target/classes";
        String packagePrefix = (args.length > 1) ? args[1] : "org.example";

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

        for (String className : classNames) {
            ClassType classType = view.getIdentifierFactory().getClassType(className);
            Optional<JavaSootClass> opt = view.getClass(classType);

            if (opt.isEmpty()) {
                System.err.println("Class not found in view: " + className);
                continue;
            }

            JavaSootClass sc = opt.get();
            System.out.println("\n================ CLASS: " + className + " ================");

            for (JavaSootMethod m : sc.getMethods()) {
                System.out.println("\n--- METHOD: " + m.getName() + " ---");

                // CORRECT: getBody() returns Body directly, not Optional
                Body body = m.getBody();
                if (body == null) {
                    System.out.println("  <no body>");
                    continue;
                }

                StmtGraph<?> cfg = body.getStmtGraph();

                // NEW: Dependency analysis
                System.out.println("\n=== Analyzing Dependencies for: " + m.getName() + " ===");

                // Step 1: Analyze DEF/USE sets
                DefUseAnalyzer defUseAnalyzer = new DefUseAnalyzer(cfg);
                defUseAnalyzer.printDefUseSets();

                // Step 2: Create dependency analyzer
                DependencyAnalyzer depAnalyzer = new DependencyAnalyzer(cfg, defUseAnalyzer);
                DependencyResult dependencies = depAnalyzer.analyze();

                // Step 3: Print results
                dependencies.printResults();
                depAnalyzer.printReachingDefinitions();

                // Your existing CFG output code can go here...
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
}