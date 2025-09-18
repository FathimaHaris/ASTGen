package org.example.analyzer;

import junit.framework.TestCase;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.core.model.Body;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class LoopProgramTest extends TestCase {

    private JavaView view;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        List<AnalysisInputLocation> inputLocations = List.of(
                new JavaClassPathAnalysisInputLocation("target/test-classes"),
                new JavaClassPathAnalysisInputLocation("target/classes")
        );
        view = new JavaView(inputLocations);
    }

    private Optional<JavaSootClass> getClass(String className) {
        return view.getClass(view.getIdentifierFactory().getClassType(className));
    }

    // === 1️⃣ Def-Use Analysis ===
    public void testDefUse() {
        String className = "org.example.programs.LoopTest";
        getClass(className).ifPresent(sc -> sc.getMethods().forEach(m -> {
            Body body = m.getBody();
            if (body == null) return;

            StmtGraph<?> cfg = body.getStmtGraph();
            var defUse = new org.example.analyzer.dependency.DefUseAnalyzer(cfg);

            System.out.println("\n=== DEF/USE ANALYSIS for " + m.getName() + " ===");
            for (Stmt stmt : cfg.getNodes()) {
                Set<String> defs = defUse.getDefSet(stmt);
                Set<String> uses = defUse.getUseSet(stmt);
                System.out.println("Stmt: " + stmt);
                System.out.println("  DEF: " + defs);
                System.out.println("  USE: " + uses);
                System.out.println("---");
            }
        }));
    }

    // === 2️⃣ Reaching Definitions ===
    public void testReachingDefinitions() {
        String className = "org.example.programs.LoopTest";
        getClass(className).ifPresent(sc -> sc.getMethods().forEach(m -> {
            Body body = m.getBody();
            if (body == null) return;

            StmtGraph<?> cfg = body.getStmtGraph();
            var defUse = new org.example.analyzer.dependency.DefUseAnalyzer(cfg);
            var depAnalyzer = new org.example.analyzer.dependency.DependencyAnalyzer(cfg, defUse);

            System.out.println("\n=== REACHING DEFINITIONS for " + m.getName() + " ===");
            for (Stmt stmt : cfg.getNodes()) {
                Set<Stmt> reaching = depAnalyzer.getReachingDefinitions().get(stmt);
                System.out.println("Stmt: " + stmt);
                System.out.println("  Reaching defs (" + (reaching != null ? reaching.size() : 0) + "): " + reaching);
                System.out.println("---");
            }
        }));
    }

    // === 3️⃣ Control Dependencies ===
    public void testControlDependencies() {
        String className = "org.example.programs.LoopTest";
        getClass(className).ifPresent(sc -> sc.getMethods().forEach(m -> {
            Body body = m.getBody();
            if (body == null) return;

            StmtGraph<?> cfg = body.getStmtGraph();
            var defUse = new org.example.analyzer.dependency.DefUseAnalyzer(cfg);
            var depAnalyzer = new org.example.analyzer.dependency.DependencyAnalyzer(cfg, defUse);

            var result = depAnalyzer.analyze(); // populates control dependencies

            System.out.println("\n=== CONTROL DEPENDENCIES for " + m.getName() + " ===");
            result.getControlDependencies().forEach((stmt, branches) -> {
                System.out.println("Stmt: " + stmt);
                System.out.println("  Control dependent on: " + branches);
                System.out.println("---");
            });
        }));
    }

    // === 4️⃣ Loop Analysis ===
    public void testLoopAnalysis() {
        String className = "org.example.programs.LoopTest";
        getClass(className).ifPresent(sc -> sc.getMethods().forEach(m -> {
            Body body = m.getBody();
            if (body == null) return;

            StmtGraph<?> cfg = body.getStmtGraph();
            var defUse = new org.example.analyzer.dependency.DefUseAnalyzer(cfg);
            var depAnalyzer = new org.example.analyzer.dependency.DependencyAnalyzer(cfg, defUse);

            System.out.println("\n=== LOOP ANALYSIS for " + m.getName() + " ===");
            depAnalyzer.printLoopAnalysis();
        }));
    }
}
