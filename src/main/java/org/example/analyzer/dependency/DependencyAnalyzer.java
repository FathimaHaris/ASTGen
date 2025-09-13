package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import java.util.*;

public class DependencyAnalyzer {
    private StmtGraph<?> cfg;
    private DefUseAnalyzer defUseAnalyzer;
    private Map<Stmt, Set<Stmt>> reachingDefinitions;
    private DominatorAnalyzer dominatorAnalyzer;

    public DependencyAnalyzer(StmtGraph<?> cfg, DefUseAnalyzer defUseAnalyzer) {
        this.cfg = cfg;
        this.defUseAnalyzer = defUseAnalyzer;
        this.dominatorAnalyzer = new DominatorAnalyzer(cfg);
        this.reachingDefinitions = new HashMap<>();
    }

    public DependencyResult analyze() {
        DependencyResult result = new DependencyResult();

        // Step 1: Perform reaching definitions analysis
        analyzeReachingDefinitions();

        // Step 2: Analyze data dependencies
        analyzeDataDependencies(result);

        // Step 3: Analyze control dependencies
        analyzeControlDependencies(result);

        // Step 4: Analyze loop dependencies
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
                    outSet.add(stmt); // This statement generates new definitions
                }

                // KILL: Remove definitions killed by this statement
                // (other statements that define the same variables)
                for (Stmt defStmt : newInSet) {
                    Set<String> defVars = defUseAnalyzer.getDefSet(defStmt);
                    Set<String> currentDefVars = defUseAnalyzer.getDefSet(stmt);

                    // If this statement defines variables that were defined elsewhere, kill those old definitions
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
                // Find which reaching definitions define this variable
                for (Stmt defStmt : reachingDefinitions.get(stmt)) {
                    Set<String> defVars = defUseAnalyzer.getDefSet(defStmt);

                    if (defVars.contains(usedVar)) {
                        // Found a RAW (Read After Write) dependency!
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

            // Check for WAR and WAW dependencies
            analyzeAntiAndOutputDependencies(stmt, result);
        }
    }

    private void analyzeAntiAndOutputDependencies(Stmt stmt, DependencyResult result) {
        Set<String> defVars = defUseAnalyzer.getDefSet(stmt);

        for (String defVar : defVars) {
            // Look for statements that use this variable before it's redefined
            for (Stmt otherStmt : reachingDefinitions.keySet()) {
                if (otherStmt.equals(stmt)) continue;

                Set<String> otherUses = defUseAnalyzer.getUseSet(otherStmt);
                if (otherUses.contains(defVar)) {
                    // WAR (Write After Read) - anti-dependency
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
                    // WAW (Write After Write) - output dependency
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
        // Simplified control dependency analysis
        // In real implementation, we'd use post-dominator trees

        for (Object node : cfg.getNodes()) {
            if (!(node instanceof Stmt)) continue;

            Stmt stmt = (Stmt) node;

            // Look for branch statements that control this statement
            for (Object predObj : cfg.predecessors(stmt)) {
                if (predObj instanceof Stmt) {
                    Stmt pred = (Stmt) predObj;

                    // If predecessor is a branch statement (if, goto, etc.)
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
        // Simple heuristic - real implementation would use proper type checking
        return stmtStr.contains("if ") || stmtStr.contains("goto") ||
                stmtStr.contains("switch") || stmtStr.contains("break");
    }

    private void analyzeLoopDependencies(DependencyResult result) {
        System.out.println("\n=== ANALYZING LOOP DEPENDENCIES ===");
        // Simplified loop analysis
        // Real implementation would identify natural loops and analyze carried dependencies

        // For now, we'll just detect simple loop patterns
        detectSimpleLoops(result);
    }

    private void detectSimpleLoops(DependencyResult result) {
        // Very basic loop detection - looks for backward edges in CFG
        Set<Stmt> visited = new HashSet<>();
        Set<Stmt> inLoop = new HashSet<>();

        for (Object node : cfg.getNodes()) {
            if (!(node instanceof Stmt)) continue;

            Stmt stmt = (Stmt) node;
            if (!visited.contains(stmt)) {
                detectLoopsDFS(stmt, visited, new HashSet<>(), inLoop, result);
            }
        }

        // Analyze loop-carried dependencies
        analyzeLoopCarriedDependencies(inLoop, result);
    }

    private void detectLoopsDFS(Stmt current, Set<Stmt> visited, Set<Stmt> stack,
                                Set<Stmt> inLoop, DependencyResult result) {
        visited.add(current);
        stack.add(current);

        for (Object succObj : cfg.successors(current)) {
            if (succObj instanceof Stmt) {
                Stmt succ = (Stmt) succObj;

                if (stack.contains(succ)) {
                    // Found a back edge - loop detected!
                    inLoop.add(current);
                    inLoop.add(succ);
                    System.out.println("Loop detected between: " + current + " and " + succ);
                } else if (!visited.contains(succ)) {
                    detectLoopsDFS(succ, visited, stack, inLoop, result);
                }
            }
        }

        stack.remove(current);
    }

    private void analyzeLoopCarriedDependencies(Set<Stmt> loopStatements, DependencyResult result) {
        for (Stmt stmt : loopStatements) {
            Set<String> defVars = defUseAnalyzer.getDefSet(stmt);

            for (String var : defVars) {
                // Check if this variable is used in other loop statements
                // (simplified loop-carried dependency detection)
                for (Stmt otherStmt : loopStatements) {
                    if (!otherStmt.equals(stmt)) {
                        Set<String> uses = defUseAnalyzer.getUseSet(otherStmt);
                        if (uses.contains(var)) {
                            LoopDependency loopDep = new LoopDependency(
                                    true,  // carried dependency
                                    var,
                                    1      // distance 1 (simplified)
                            );
                            result.addLoopDependency(otherStmt, loopDep);
                        }
                    }
                }
            }
        }
    }

    // Helper method to print reaching definitions
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
}