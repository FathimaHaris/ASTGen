package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import java.util.*;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.common.stmt.BranchingStmt;
import sootup.core.jimple.common.stmt.JGotoStmt;



public class DependencyAnalyzer {
    private StmtGraph<?> cfg;
    private DefUseAnalyzer defUseAnalyzer;
    private Map<Stmt, Set<Stmt>> reachingDefinitions;
    private DominatorAnalyzer dominatorAnalyzer;
    private LoopAnalyzer loopAnalyzer;
    private PostDominatorAnalyzer postDominatorAnalyzer;


    public DependencyAnalyzer(StmtGraph<?> cfg, DefUseAnalyzer defUseAnalyzer) {
        this.cfg = cfg;
        this.defUseAnalyzer = defUseAnalyzer;
        this.reachingDefinitions = new HashMap<>();

        analyzeReachingDefinitions();
        this.dominatorAnalyzer = new DominatorAnalyzer(cfg);
        this.postDominatorAnalyzer = new PostDominatorAnalyzer(cfg);

        this.loopAnalyzer = new LoopAnalyzer(cfg, dominatorAnalyzer, defUseAnalyzer, reachingDefinitions);
    }

    public DependencyResult analyze() {
        DependencyResult result = new DependencyResult();

        analyzeDataDependencies(result);
        analyzeControlDependencies(result);
        analyzeLoopDependencies(result);

        return result;
    }

    private void analyzeReachingDefinitions() {
        // Initialize
        for (Stmt stmt : cfg.getStmts()) {
            reachingDefinitions.put(stmt, new HashSet<>());
        }

        boolean changed;
        do {
            changed = false;

            for (Stmt stmt : cfg.getStmts()) {
                Set<Stmt> newInSet = new HashSet<>();

                // Union of predecessors' OUT sets
                for (Stmt pred : cfg.predecessors(stmt)) {
                    newInSet.addAll(reachingDefinitions.get(pred));
                }

                // Apply GEN and KILL
                Set<Stmt> outSet = new HashSet<>(newInSet);

                // GEN: This statement defines variables
                if (!defUseAnalyzer.getDefSet(stmt).isEmpty()) {
                    outSet.add(stmt);
                }

                // KILL: Remove definitions that are killed by this statement
                Set<String> currentDefs = defUseAnalyzer.getDefSet(stmt);
                Iterator<Stmt> iter = outSet.iterator();
                while (iter.hasNext()) {
                    Stmt defStmt = iter.next();
                    Set<String> defVars = defUseAnalyzer.getDefSet(defStmt);
                    for (String var : currentDefs) {
                        if (defVars.contains(var)) {
                            iter.remove();
                            break;
                        }
                    }
                }

                if (!outSet.equals(reachingDefinitions.get(stmt))) {
                    reachingDefinitions.put(stmt, outSet);
                    changed = true;
                }
            }
        } while (changed);
    }

    private void analyzeDataDependencies(DependencyResult result) {
        for (Stmt stmt : cfg.getStmts()) {
            Set<String> usedVars = defUseAnalyzer.getUseSet(stmt);

            for (String usedVar : usedVars) {
                for (Stmt defStmt : reachingDefinitions.get(stmt)) {
                    Set<String> defVars = defUseAnalyzer.getDefSet(defStmt);
                    if (defVars.contains(usedVar)) {
                        Dependency dep = new Dependency(Dependency.Type.RAW, defStmt, stmt, usedVar);
                        result.addDataDependency(dep);
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

                Set<String> otherDefs = defUseAnalyzer.getDefSet(otherStmt);

                if (otherDefs.contains(defVar)) {
                    // Existing WAW dependency
                    Dependency wawDep = new Dependency(Dependency.Type.WAW, otherStmt, stmt, defVar);
                    result.addDataDependency(wawDep);

                    // New DEF_ORDER dependency
                    if (dominatorAnalyzer.dominates(otherStmt, stmt)) {
                        Dependency defOrderDep = new Dependency(Dependency.Type.DEF_ORDER, otherStmt, stmt, defVar);
                        result.addDataDependency(defOrderDep);
                    }
                }

                Set<String> otherUses = defUseAnalyzer.getUseSet(otherStmt);
                if (otherUses.contains(defVar)) {
                    Dependency warDep = new Dependency(Dependency.Type.WAR, otherStmt, stmt, defVar);
                    result.addDataDependency(warDep);
                }
            }
        }
    }


    private void analyzeControlDependencies(DependencyResult result) {
        for (Stmt branch : cfg.getStmts()) {
            if (isBranchStatement(branch)) {
                for (Stmt succ : cfg.successors(branch)) {
                    // succ is control dependent on branch
                    // if succ does NOT post-dominate branch
                    if (!postDominatorAnalyzer.postDominates(succ, branch)) {
                        result.addControlDependency(succ, branch);
                    }
                }
            }
        }
    }


    private boolean isBranchStatement(Stmt stmt) {
        return stmt instanceof JIfStmt || stmt instanceof JGotoStmt || stmt instanceof BranchingStmt ;

    }

    private void analyzeLoopDependencies(DependencyResult result) {
        loopAnalyzer.analyzeLoopDependencies(result);
    }

    // Helper methods
    public void printReachingDefinitions() {
        System.out.println("\n=== REACHING DEFINITIONS ===");
        for (Stmt stmt : reachingDefinitions.keySet()) {
            System.out.println("Stmt: " + stmt);
            System.out.println("  Reaching defs: " + reachingDefinitions.get(stmt).size());
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