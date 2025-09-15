package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import java.util.*;

public class DependencyAnalyzer {
    private StmtGraph<?> cfg;
    private DefUseAnalyzer defUseAnalyzer;
    private Map<Stmt, Set<Stmt>> reachingDefinitions;
    private DominatorAnalyzer dominatorAnalyzer;
    private LoopAnalyzer loopAnalyzer;

    public DependencyAnalyzer(StmtGraph<?> cfg, DefUseAnalyzer defUseAnalyzer) {
        this.cfg = cfg;
        this.defUseAnalyzer = defUseAnalyzer;
        this.reachingDefinitions = new HashMap<>();

        // Step 1: Compute reaching definitions FIRST
        analyzeReachingDefinitions();

        // Step 2: Initialize dominator analyzer
        this.dominatorAnalyzer = new DominatorAnalyzer(cfg);

        // Step 3: Initialize loop analyzer with ALL required parameters
        this.loopAnalyzer = new LoopAnalyzer(cfg, dominatorAnalyzer, defUseAnalyzer, reachingDefinitions);
    }

    public DependencyResult analyze() {
        DependencyResult result = new DependencyResult();

        // Step 1: Analyze data dependencies
        analyzeDataDependencies(result);

        // Step 2: Analyze control dependencies
        analyzeControlDependencies(result);

        // Step 3: Analyze loop dependencies
        analyzeLoopDependencies(result);

        return result;
    }

    private void analyzeReachingDefinitions() {
        // Initialize reaching definitions for each statement
        for (Object node : cfg.getNodes()) {
            if (node instanceof Stmt) {
                Stmt stmt = (Stmt) node;
                reachingDefinitions.put(stmt, new HashSet<>());
            }
        }

        // Worklist algorithm for reaching definitions
        boolean changed;
        do {
            changed = false;

            for (Object node : cfg.getNodes()) {
                if (!(node instanceof Stmt)) continue;

                Stmt stmt = (Stmt) node;
                Set<Stmt> newInSet = new HashSet<>();

                // Get reaching definitions from all predecessors
                for (Object predObj : cfg.predecessors(stmt)) {
                    if (predObj instanceof Stmt) {
                        Stmt pred = (Stmt) predObj;
                        newInSet.addAll(reachingDefinitions.get(pred));
                    }
                }

                // Apply GEN and KILL sets
                Set<Stmt> outSet = new HashSet<>(newInSet);

                // GEN: This statement's definitions
                if (!defUseAnalyzer.getDefSet(stmt).isEmpty()) {
                    outSet.add(stmt);
                }

                // KILL: Remove definitions killed by this statement
                for (Stmt defStmt : newInSet) {
                    Set<String> defVars = defUseAnalyzer.getDefSet(defStmt);
                    Set<String> currentDefVars = defUseAnalyzer.getDefSet(stmt);

                    for (String var : currentDefVars) {
                        if (defVars.contains(var)) {
                            outSet.remove(defStmt);
                        }
                    }
                }

                // Check if reaching definitions changed
                if (!outSet.equals(reachingDefinitions.get(stmt))) {
                    reachingDefinitions.put(stmt, outSet);
                    changed = true;
                }
            }
        } while (changed);
    }

    private void analyzeDataDependencies(DependencyResult result) {
        System.out.println("\n=== ANALYZING DATA DEPENDENCIES ===");

        for (Object node : cfg.getNodes()) {
            if (!(node instanceof Stmt)) continue;

            Stmt stmt = (Stmt) node;
            Set<String> usedVars = defUseAnalyzer.getUseSet(stmt);

            System.out.println("Analyzing stmt: " + stmt);
            System.out.println("  Uses: " + usedVars);
            System.out.println("  Reaching defs: " + reachingDefinitions.get(stmt).size());

            for (String usedVar : usedVars) {
                for (Stmt defStmt : reachingDefinitions.get(stmt)) {
                    Set<String> defVars = defUseAnalyzer.getDefSet(defStmt);

                    if (defVars.contains(usedVar)) {
                        Dependency dep = new Dependency(
                                Dependency.Type.RAW,
                                defStmt,
                                stmt,
                                usedVar
                        );
                        result.addDataDependency(dep);
                        System.out.println("  FOUND RAW: " + defStmt + " → " + stmt + " for var: " + usedVar);
                    }
                }
            }

            analyzeAntiAndOutputDependencies(stmt, result);
        }
    }

    private void analyzeAntiAndOutputDependencies(Stmt stmt, DependencyResult result) {
        Set<String> defVars = defUseAnalyzer.getDefSet(stmt);

        for (String defVar : defVars) {
            for (Stmt otherStmt : reachingDefinitions.keySet()) {
                if (otherStmt.equals(stmt)) continue;

                Set<String> otherUses = defUseAnalyzer.getUseSet(otherStmt);
                if (otherUses.contains(defVar)) {
                    Dependency dep = new Dependency(
                            Dependency.Type.WAR,
                            otherStmt,
                            stmt,
                            defVar
                    );
                    result.addDataDependency(dep);
                }

                Set<String> otherDefs = defUseAnalyzer.getDefSet(otherStmt);
                if (otherDefs.contains(defVar)) {
                    Dependency dep = new Dependency(
                            Dependency.Type.WAW,
                            otherStmt,
                            stmt,
                            defVar
                    );
                    result.addDataDependency(dep);
                }
            }
        }
    }

    private void analyzeControlDependencies(DependencyResult result) {
        System.out.println("\n=== ANALYZING CONTROL DEPENDENCIES ===");

        for (Object node : cfg.getNodes()) {
            if (!(node instanceof Stmt)) continue;

            Stmt stmt = (Stmt) node;

            for (Object predObj : cfg.predecessors(stmt)) {
                if (predObj instanceof Stmt) {
                    Stmt pred = (Stmt) predObj;

                    if (isBranchStatement(pred)) {
                        result.addControlDependency(stmt, pred);
                        System.out.println("Control dep: " + pred + " → " + stmt);
                    }
                }
            }
        }
    }

    private boolean isBranchStatement(Stmt stmt) {
        String stmtStr = stmt.toString();
        return stmtStr.contains("if ") || stmtStr.contains("goto") ||
                stmtStr.contains("switch") || stmtStr.contains("break");
    }

    private void analyzeLoopDependencies(DependencyResult result) {
        System.out.println("\n=== ANALYZING LOOP DEPENDENCIES ===");
        // Use the proper loop analyzer instead of simple detection
        loopAnalyzer.analyzeLoopDependencies(result);
    }

    // Helper methods
    public void printReachingDefinitions() {
        System.out.println("\n=== REACHING DEFINITIONS ===");
        for (Stmt stmt : reachingDefinitions.keySet()) {
            System.out.println("Stmt: " + stmt);
            System.out.println("  Reaching defs: " + reachingDefinitions.get(stmt).size());
            for (Stmt def : reachingDefinitions.get(stmt)) {
                System.out.println("    - " + def);
            }
        }
    }

    public void printDominatorAnalysis() {
        dominatorAnalyzer.printDominators();
        dominatorAnalyzer.printDominatorTree();
    }

    public void printLoopAnalysis() {
        loopAnalyzer.printLoopAnalysis();
    }
}