package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import java.util.*;

public class LoopAnalyzer {
    private StmtGraph<?> cfg;
    private DominatorAnalyzer dominatorAnalyzer;
    private DefUseAnalyzer defUseAnalyzer;
    private Map<Stmt, Set<Stmt>> reachingDefinitions;
    private Map<Stmt, Loop> loops;
    private Map<Stmt, Set<LoopDependency>> loopDependencies;
    private DependencyResult internalResult; // Store result internally

    public LoopAnalyzer(StmtGraph<?> cfg, DominatorAnalyzer dominatorAnalyzer,
                        DefUseAnalyzer defUseAnalyzer, Map<Stmt, Set<Stmt>> reachingDefinitions) {
        this.cfg = cfg;
        this.dominatorAnalyzer = dominatorAnalyzer;
        this.defUseAnalyzer = defUseAnalyzer;
        this.reachingDefinitions = reachingDefinitions;
        this.loops = new HashMap<>();
        this.loopDependencies = new HashMap<>();
        this.internalResult = new DependencyResult(); // Initialize internal result
        this.analyze(); // Initialize loops when created
    }

    private void analyze() {
        findNaturalLoops();
        resolveNestedLoops();
        analyzeLoopDependencies(); // Now this works without parameters
    }

    // Internal method for initialization
    private void analyzeLoopDependencies() {
        System.out.println("\n=== ANALYZING LOOP DEPENDENCIES (INTERNAL) ===");

        for (Loop loop : loops.values()) {
            analyzeDependenciesInLoop(loop, internalResult);
        }
    }

    // Public method for external use
    public void analyzeLoopDependencies(DependencyResult result) {
        System.out.println("\n=== ANALYZING LOOP DEPENDENCIES (EXTERNAL) ===");

        // Copy internal results to the provided result
        for (Map.Entry<Stmt, Set<LoopDependency>> entry : loopDependencies.entrySet()) {
            for (LoopDependency dep : entry.getValue()) {
                result.addLoopDependency(entry.getKey(), dep);
            }
        }
    }

    private void analyzeDependenciesInLoop(Loop loop, DependencyResult result) {
        System.out.println("Analyzing dependencies in loop: " + loop.getHeader());

        // Get all statements in this loop
        Set<Stmt> loopStatements = loop.getStatements();

        for (Stmt stmt : loopStatements) {
            loopDependencies.putIfAbsent(stmt, new HashSet<>());

            // Analyze data dependencies within the loop
            analyzeDataDependenciesInLoop(stmt, loop, loopStatements, result);
        }
    }

    private void analyzeDataDependenciesInLoop(Stmt stmt, Loop loop, Set<Stmt> loopStatements, DependencyResult result) {
        Set<String> uses = defUseAnalyzer.getUseSet(stmt);

        for (String usedVar : uses) {
            // Find definitions that reach this statement
            for (Stmt defStmt : reachingDefinitions.get(stmt)) {
                if (loopStatements.contains(defStmt)) {
                    // Both statements are in the same loop
                    Set<String> defs = defUseAnalyzer.getDefSet(defStmt);

                    if (defs.contains(usedVar)) {
                        // This is a loop dependency!
                        analyzeLoopDependencyType(defStmt, stmt, usedVar, loop, result);
                    }
                }
            }
        }
    }

    private void analyzeLoopDependencyType(Stmt defStmt, Stmt useStmt, String variable, Loop loop, DependencyResult result) {
        LoopDependency.Type type;
        int distance = 0;

        if (isLoopCarriedDependency(defStmt, useStmt, variable, loop)) {
            type = LoopDependency.Type.CARRIED;
            distance = calculateDependencyDistance(defStmt, useStmt, variable, loop);
        } else {
            type = LoopDependency.Type.INDEPENDENT;
        }

        LoopDependency dep = new LoopDependency(type, variable, distance, defStmt, useStmt, loop);
        loopDependencies.get(useStmt).add(dep);

        // Add to the overall result
        result.addLoopDependency(useStmt, dep);

        System.out.println("  Loop dependency: " + dep);
    }


    private boolean isLoopCarriedDependency(Stmt defStmt, Stmt useStmt, String variable, Loop loop) {
        // Check if this is a loop-carried dependency
        // Basic heuristic: if it involves array access or induction variables

        String defStr = defStmt.toString();
        String useStr = useStmt.toString();

        // Array access pattern: a[i] → a[i+1]
        if (defStr.contains("[") && useStr.contains("[")) {
            return true;
        }

        // Induction variable pattern: i → i+1
        if (defStr.contains("i =") && useStr.contains("i +")) {
            return true;
        }

        // Default: assume independent for now
        return false;
    }

    private int calculateDependencyDistance(Stmt defStmt, Stmt useStmt, String variable, Loop loop) {
        // Simple distance calculation
        // Real implementation would use induction variable analysis

        String defStr = defStmt.toString();
        String useStr = useStmt.toString();

        // Array access patterns
        if (defStr.contains("i]") && useStr.contains("i+1]")) return 1;
        if (defStr.contains("i]") && useStr.contains("i+2]")) return 2;
        if (defStr.contains("i]") && useStr.contains("i-1]")) return -1;

        // Induction variable patterns
        if (defStr.contains("i =") && useStr.contains("i + 1")) return 1;
        if (defStr.contains("i =") && useStr.contains("i + 2")) return 2;

        return 1; // Default distance
    }

    private void findNaturalLoops() {
        System.out.println("\n=== FINDING NATURAL LOOPS ===");

        // Step 1: Find back edges (edge from node to its dominator)
        for (Object node : cfg.getNodes()) {
            if (!(node instanceof Stmt)) continue;

            Stmt source = (Stmt) node;

            for (Object succObj : cfg.successors(source)) {
                if (succObj instanceof Stmt) {
                    Stmt target = (Stmt) succObj;

                    // Check if target dominates source (back edge condition)
                    if (dominatorAnalyzer.dominates(target, source)) {
                        System.out.println("Found back edge: " + source + " → " + target);

                        // target is the loop header
                        Loop loop = loops.computeIfAbsent(target, k -> new Loop(target));

                        // Find all statements in the loop body
                        findLoopBody(loop, source);
                    }
                }
            }
        }
    }

    private void findLoopBody(Loop loop, Stmt backEdgeSource) {
        Stack<Stmt> worklist = new Stack<>();
        worklist.push(backEdgeSource);

        while (!worklist.isEmpty()) {
            Stmt current = worklist.pop();

            if (!loop.contains(current)) {
                loop.addStatement(current);
                System.out.println("  Added to loop: " + current);

                // Add all predecessors except the header
                for (Object predObj : cfg.predecessors(current)) {
                    if (predObj instanceof Stmt) {
                        Stmt pred = (Stmt) predObj;
                        if (!pred.equals(loop.getHeader())) {
                            worklist.push(pred);
                        }
                    }
                }
            }
        }
    }

    private void resolveNestedLoops() {
        System.out.println("\n=== RESOLVING NESTED LOOPS ===");

        List<Loop> loopList = new ArrayList<>(loops.values());

        for (int i = 0; i < loopList.size(); i++) {
            for (int j = i + 1; j < loopList.size(); j++) {
                Loop loop1 = loopList.get(i);
                Loop loop2 = loopList.get(j);

                if (isNested(loop1, loop2)) {
                    System.out.println("Nested loop: " + loop2.getHeader() + " inside " + loop1.getHeader());
                    loop1.addNestedLoop(loop2);
                } else if (isNested(loop2, loop1)) {
                    System.out.println("Nested loop: " + loop1.getHeader() + " inside " + loop2.getHeader());
                    loop2.addNestedLoop(loop1);
                }
            }
        }
    }

    private boolean isNested(Loop outer, Loop inner) {
        // Check if inner loop is completely contained within outer loop
        // but not equal to outer loop
        if (outer.equals(inner)) {
            return false;
        }

        for (Stmt stmt : inner.getStatements()) {
            if (!outer.contains(stmt)) {
                return false;
            }
        }
        return true;
    }

    // Public API
    public Map<Stmt, Loop> getLoops() {
        return Collections.unmodifiableMap(loops);
    }

    public boolean isInLoop(Stmt stmt) {
        for (Loop loop : loops.values()) {
            if (loop.contains(stmt)) {
                return true;
            }
        }
        return false;
    }

    public Loop getLoopForStatement(Stmt stmt) {
        for (Loop loop : loops.values()) {
            if (loop.contains(stmt)) {
                return loop;
            }
        }
        return null;
    }

    public Set<LoopDependency> getLoopDependencies(Stmt stmt) {
        return Collections.unmodifiableSet(loopDependencies.getOrDefault(stmt, new HashSet<>()));
    }

    public Map<Stmt, Set<LoopDependency>> getAllLoopDependencies() {
        return Collections.unmodifiableMap(loopDependencies);
    }

    public void printLoopAnalysis() {
        System.out.println("\n=== LOOP ANALYSIS RESULTS ===");

        if (loops.isEmpty()) {
            System.out.println("No loops found in the method");
            return;
        }

        for (Loop loop : loops.values()) {
            printLoopInfo(loop, 0);
        }
    }

    private void printLoopInfo(Loop loop, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }

        System.out.println(indent + "Loop Header: " + loop.getHeader());
        System.out.println(indent + "Statements: " + loop.getStatements().size());
        System.out.println(indent + "Nesting Depth: " + loop.getNestingDepth());

        for (Stmt stmt : loop.getStatements()) {
            System.out.println(indent + "  - " + stmt);
        }

        for (Loop nested : loop.getNestedLoops()) {
            printLoopInfo(nested, depth + 1);
        }
    }
}
