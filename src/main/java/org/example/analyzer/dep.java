package org.example.analyzer;


import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.JGotoStmt;
import sootup.core.jimple.common.expr.JimpleLocal;
import sootup.core.jimple.basic.Local;
import java.util.*;

public class dep {

    public static DependencyResult analyzeDependencies(StmtGraph<?> cfg) {
        DependencyResult result = new DependencyResult();

        // 1. Data Dependency Analysis (Def-Use chains)
        analyzeDataDependencies(cfg, result);

        // 2. Control Dependency Analysis
        analyzeControlDependencies(cfg, result);

        // 3. Loop Analysis
        analyzeLoopDependencies(cfg, result);

        return result;
    }

    // ==================== DATA DEPENDENCY ANALYSIS ====================
    private static void analyzeDataDependencies(StmtGraph<?> cfg, DependencyResult result) {
        Map<String, List<Stmt>> defSites = new HashMap<>(); // variable -> list of def statements
        Map<String, List<Stmt>> useSites = new HashMap<>(); // variable -> list of use statements

        // First pass: Collect all def and use sites
        for (Stmt stmt : cfg.getNodes()) {
            if (stmt instanceof JAssignStmt) {
                JAssignStmt assign = (JAssignStmt) stmt;

                // Handle definition
                String defVar = extractVariable(assign.getLeftOp().toString());
                if (defVar != null) {
                    defSites.computeIfAbsent(defVar, k -> new ArrayList<>()).add(stmt);
                    result.addDefinition(defVar, stmt);
                }

                // Handle uses in right-hand side
                extractVariables(assign.getRightOp().toString()).forEach(useVar -> {
                    useSites.computeIfAbsent(useVar, k -> new ArrayList<>()).add(stmt);
                    result.addUse(useVar, stmt);
                });
            } else {
                // Handle uses in other statements (method calls, etc.)
                extractVariables(stmt.toString()).forEach(useVar -> {
                    useSites.computeIfAbsent(useVar, k -> new ArrayList<>()).add(stmt);
                    result.addUse(useVar, stmt);
                });
            }
        }

        // Second pass: Build def-use chains
        buildDefUseChains(defSites, useSites, cfg, result);
    }

    private static void buildDefUseChains(Map<String, List<Stmt>> defSites,
                                          Map<String, List<Stmt>> useSites,
                                          StmtGraph<?> cfg,
                                          DependencyResult result) {

        for (String variable : useSites.keySet()) {
            List<Stmt> defs = defSites.getOrDefault(variable, new ArrayList<>());
            List<Stmt> uses = useSites.get(variable);

            for (Stmt useStmt : uses) {
                for (Stmt defStmt : defs) {
                    if (isReachable(defStmt, useStmt, cfg) && !isKilled(defStmt, useStmt, variable, cfg)) {
                        result.addDataDependency(defStmt, useStmt, variable);
                    }
                }
            }
        }
    }

    // ==================== CONTROL DEPENDENCY ANALYSIS ====================
    private static void analyzeControlDependencies(StmtGraph<?> cfg, DependencyResult result) {
        // Find all conditional branches (if statements)
        for (Stmt stmt : cfg.getNodes()) {
            if (stmt instanceof JIfStmt) {
                JIfStmt ifStmt = (JIfStmt) stmt;

                // Get both branches (true and false)
                Set<Stmt> controlledStmts = findControlledStatements(ifStmt, cfg);

                for (Stmt controlledStmt : controlledStmts) {
                    result.addControlDependency(ifStmt, controlledStmt);
                }
            }
        }
    }

    private static Set<Stmt> findControlledStatements(JIfStmt ifStmt, StmtGraph<?> cfg) {
        Set<Stmt> controlled = new HashSet<>();
        Stmt immediateSuccessor = cfg.successors(ifStmt).iterator().next();

        // Use post-dominator analysis to find controlled statements
        // Simplified: all statements in the branch until merge point
        findStatementsInBranch(immediateSuccessor, ifStmt, cfg, controlled, new HashSet<>());

        return controlled;
    }

    // ==================== LOOP DEPENDENCY ANALYSIS ====================
    private static void analyzeLoopDependencies(StmtGraph<?> cfg, DependencyResult result) {
        // Identify loops in the CFG
        Map<Stmt, LoopInfo> loops = identifyLoops(cfg);

        for (LoopInfo loop : loops.values()) {
            // Analyze dependencies within this loop
            analyzeDependenciesInLoop(loop, cfg, result);
        }
    }

    private static void analyzeDependenciesInLoop(LoopInfo loop, StmtGraph<?> cfg, DependencyResult result) {
        // For each data dependency in the loop
        for (DataDependency dep : result.dataDependencies) {
            if (loop.containsStatement(dep.defStmt) && loop.containsStatement(dep.useStmt)) {
                if (isLoopCarried(dep.defStmt, dep.useStmt, loop, cfg)) {
                    result.addLoopCarriedDependency(dep.defStmt, dep.useStmt, dep.variable);
                } else {
                    result.addLoopIndependentDependency(dep.defStmt, dep.useStmt, dep.variable);
                }
            }
        }
    }

    private static boolean isLoopCarried(Stmt defStmt, Stmt useStmt, LoopInfo loop, StmtGraph<?> cfg) {
        // A dependency is loop-carried if the definition reaches across iterations
        // Simple heuristic: if there's a path from use back to def within the loop
        return isReachable(useStmt, defStmt, cfg) && loop.containsStatement(defStmt) && loop.containsStatement(useStmt);
    }

    // ==================== UTILITY METHODS ====================
    private static boolean isReachable(Stmt from, Stmt to, StmtGraph<?> cfg) {
        // BFS to check if 'to' is reachable from 'from'
        Queue<Stmt> queue = new LinkedList<>();
        Set<Stmt> visited = new HashSet<>();

        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            Stmt current = queue.poll();
            if (current.equals(to)) return true;

            for (Stmt successor : cfg.successors(current)) {
                if (!visited.contains(successor)) {
                    visited.add(successor);
                    queue.add(successor);
                }
            }
        }

        return false;
    }

    private static boolean isKilled(Stmt defStmt, Stmt useStmt, String variable, StmtGraph<?> cfg) {
        // Check if another definition of the same variable occurs between def and use
        Queue<Stmt> queue = new LinkedList<>();
        Set<Stmt> visited = new HashSet<>();

        queue.add(defStmt);
        visited.add(defStmt);

        while (!queue.isEmpty()) {
            Stmt current = queue.poll();
            if (current.equals(useStmt)) return false; // Reached use without encountering another def

            if (current instanceof JAssignStmt && !current.equals(defStmt)) {
                JAssignStmt assign = (JAssignStmt) current;
                if (extractVariable(assign.getLeftOp().toString()).equals(variable)) {
                    return true; // Found another definition
                }
            }

            for (Stmt successor : cfg.successors(current)) {
                if (!visited.contains(successor)) {
                    visited.add(successor);
                    queue.add(successor);
                }
            }
        }

        return false;
    }

    private static String extractVariable(String expression) {
        // Extract variable name from expression
        if (expression.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return expression;
        }
        return null;
    }

    private static List<String> extractVariables(String expression) {
        List<String> variables = new ArrayList<>();
        // Simple tokenization - improve with proper parsing
        String[] tokens = expression.split("[^a-zA-Z0-9_]");
        for (String token : tokens) {
            if (token.matches("[a-zA-Z_][a-zA-Z0-9_]*") &&
                    !token.equals("true") && !token.equals("false") &&
                    !token.equals("null")) {
                variables.add(token);
            }
        }
        return variables;
    }

    // ==================== SUPPORTING CLASSES ====================
    public static class DependencyResult {
        public List<DataDependency> dataDependencies = new ArrayList<>();
        public List<ControlDependency> controlDependencies = new ArrayList<>();
        public List<LoopDependency> loopCarriedDependencies = new ArrayList<>();
        public List<LoopDependency> loopIndependentDependencies = new ArrayList<>();
        public Map<String, List<Stmt>> definitions = new HashMap<>();
        public Map<String, List<Stmt>> uses = new HashMap<>();

        public void addDataDependency(Stmt defStmt, Stmt useStmt, String variable) {
            dataDependencies.add(new DataDependency(defStmt, useStmt, variable));
        }

        public void addControlDependency(Stmt controller, Stmt dependent) {
            controlDependencies.add(new ControlDependency(controller, dependent));
        }

        public void addLoopCarriedDependency(Stmt defStmt, Stmt useStmt, String variable) {
            loopCarriedDependencies.add(new LoopDependency(defStmt, useStmt, variable, true));
        }

        public void addLoopIndependentDependency(Stmt defStmt, Stmt useStmt, String variable) {
            loopIndependentDependencies.add(new LoopDependency(defStmt, useStmt, variable, false));
        }

        public void addDefinition(String variable, Stmt stmt) {
            definitions.computeIfAbsent(variable, k -> new ArrayList<>()).add(stmt);
        }

        public void addUse(String variable, Stmt stmt) {
            uses.computeIfAbsent(variable, k -> new ArrayList<>()).add(stmt);
        }
    }

    public static class DataDependency {
        public Stmt defStmt;
        public Stmt useStmt;
        public String variable;

        public DataDependency(Stmt defStmt, Stmt useStmt, String variable) {
            this.defStmt = defStmt;
            this.useStmt = useStmt;
            this.variable = variable;
        }
    }

    public static class ControlDependency {
        public Stmt controller;
        public Stmt dependent;

        public ControlDependency(Stmt controller, Stmt dependent) {
            this.controller = controller;
            this.dependent = dependent;
        }
    }

    public static class LoopDependency {
        public Stmt defStmt;
        public Stmt useStmt;
        public String variable;
        public boolean isLoopCarried;

        public LoopDependency(Stmt defStmt, Stmt useStmt, String variable, boolean isLoopCarried) {
            this.defStmt = defStmt;
            this.useStmt = useStmt;
            this.variable = variable;
            this.isLoopCarried = isLoopCarried;
        }
    }

    public static class LoopInfo {
        public Stmt header;
        public Set<Stmt> body;
        public Stmt latch;

        public boolean containsStatement(Stmt stmt) {
            return body.contains(stmt) || header.equals(stmt) || latch.equals(stmt);
        }
    }

    // Loop identification (simplified)
    private static Map<Stmt, LoopInfo> identifyLoops(StmtGraph<?> cfg) {
        Map<Stmt, LoopInfo> loops = new HashMap<>();

        // Look for back edges (edges from node to one of its dominators)
        for (Stmt source : cfg.getNodes()) {
            for (Stmt target : cfg.successors(source)) {
                if (isReachable(target, source, cfg)) {
                    // This is a back edge -> indicates a loop
                    LoopInfo loop = new LoopInfo();
                    loop.header = target;
                    loop.latch = source;
                    loop.body = findLoopBody(source, target, cfg);
                    loops.put(target, loop);
                }
            }
        }

        return loops;
    }

    private static Set<Stmt> findLoopBody(Stmt latch, Stmt header, StmtGraph<?> cfg) {
        Set<Stmt> body = new HashSet<>();
        Queue<Stmt> queue = new LinkedList<>();
        Set<Stmt> visited = new HashSet<>();

        queue.add(latch);
        visited.add(latch);

        while (!queue.isEmpty()) {
            Stmt current = queue.poll();
            body.add(current);

            if (current.equals(header)) continue;

            for (Stmt predecessor : cfg.predecessors(current)) {
                if (!visited.contains(predecessor)) {
                    visited.add(predecessor);
                    queue.add(predecessor);
                }
            }
        }

        return body;
    }
}