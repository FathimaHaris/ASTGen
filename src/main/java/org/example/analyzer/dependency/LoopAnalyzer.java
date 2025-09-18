package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.constant.IntConstant;

import java.util.*;

/**
 * LoopAnalyzer
 *
 * Finds natural loops (via back-edges + dominators), builds loop bodies,
 * then heuristically classifies loop dependencies into:
 *   - LoopDependency.Type.CARRIED
 *   - LoopDependency.Type.INDEPENDENT
 *
 * The class keeps an internal map of loops->loopDependencies and also exposes
 * a public analyzeLoopDependencies(result) method so the outer DependencyAnalyzer
 * can call it (and get the results placed into DependencyResult).
 *
 * NOTE: This is a conservative heuristic-based approach. For full precision
 * (especially for complex array subscripts and pointer aliasing) you will later
 * want to add SSA / Memory-SSA + affine-subscript solvers (GCD/Banerjee/Omega).
 */
public class LoopAnalyzer {
    private StmtGraph<?> cfg;
    private DominatorAnalyzer dominatorAnalyzer;
    private DefUseAnalyzer defUseAnalyzer;
    /**
     * reachingDefinitions as provided by DependencyAnalyzer: map from Stmt -> Set<Stmt>
     * where the set is the reaching definitions for that statement (as in your current RD).
     * We use it heuristically here.
     */
    private Map<Stmt, Set<Stmt>> reachingDefinitions;

    private Map<Stmt, Loop> loops;                             // header -> Loop
    private Map<Stmt, Set<LoopDependency>> loopDependencies;   // useStmt -> deps

    public LoopAnalyzer(StmtGraph<?> cfg,
                        DominatorAnalyzer dominatorAnalyzer,
                        DefUseAnalyzer defUseAnalyzer,
                        Map<Stmt, Set<Stmt>> reachingDefinitions) {
        this.cfg = cfg;
        this.dominatorAnalyzer = dominatorAnalyzer;
        this.defUseAnalyzer = defUseAnalyzer;
        this.reachingDefinitions = reachingDefinitions;
        this.loops = new HashMap<>();
        this.loopDependencies = new HashMap<>();
        // initial analysis to populate loops & internal loopDependencies
        analyze();
    }

    // ---------- PUBLIC API ----------

    /**
     * External entry point used by DependencyAnalyzer.
     * Copies internal loop dependency results into the provided DependencyResult.
     */
    public void analyzeLoopDependencies(DependencyResult result) {
        // ensure we have computed loops & loopDependencies (constructor already calls analyze())
        if (loops.isEmpty()) {
            analyze(); // safe-guard (shouldn't be necessary normally)
        }

        // Add each discovered LoopDependency into the caller's result
        for (Map.Entry<Stmt, Set<LoopDependency>> e : loopDependencies.entrySet()) {
            Stmt useStmt = e.getKey();
            for (LoopDependency ld : e.getValue()) {
                result.addLoopDependency(useStmt, ld);
            }
        }
    }

    // ---------- INTERNAL ANALYSIS ----------

    /** Top-level internal analysis sequence. */
    private void analyze() {
        findNaturalLoops();
        resolveNestedLoops();
        computeLoopDependencies();   // populates loopDependencies
    }

    /**
     * Find natural loops by scanning for back-edges: edge (tail -> head) where head dominates tail.
     * Each header maps to a Loop object.
     */
    private void findNaturalLoops() {
        for (Stmt tail : cfg.getStmts()) {
            for (Stmt head : cfg.successors(tail)) {
                if (dominatorAnalyzer.dominates(head, tail)) {
                    Loop loop = loops.computeIfAbsent(head, k -> new Loop(head));
                    collectLoopBody(loop, tail);
                }
            }
        }
    }

    /**
     * Given a header and a back-edge source (tail), collect all nodes in the natural loop.
     * Classic algorithm: start from tail, walk preds until header reached, include nodes.
     */
    private void collectLoopBody(Loop loop, Stmt backEdgeSource) {
        Deque<Stmt> stack = new ArrayDeque<>();
        stack.push(backEdgeSource);
        loop.addStatement(loop.getHeader()); // ensure header included

        while (!stack.isEmpty()) {
            Stmt current = stack.pop();
            if (!loop.contains(current)) {
                loop.addStatement(current);
                for (Stmt pred : cfg.predecessors(current)) {
                    // include preds until header (header will be present and dominator ensures loop)
                    if (!pred.equals(loop.getHeader())) {
                        stack.push(pred);
                    }
                }
            }
        }
    }

    /**
     * Post-process to discover nesting relationships among found loops.
     */
    private void resolveNestedLoops() {
        List<Loop> list = new ArrayList<>(loops.values());
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                Loop a = list.get(i);
                Loop b = list.get(j);
                if (isNested(a, b)) {
                    a.addNestedLoop(b);
                } else if (isNested(b, a)) {
                    b.addNestedLoop(a);
                }
            }
        }
    }

    private boolean isNested(Loop outer, Loop inner) {
        if (outer.equals(inner)) return false;
        for (Stmt s : inner.getStatements()) {
            if (!outer.contains(s)) return false;
        }
        return true;
    }

    /**
     * Compute dependencies for each loop and populate loopDependencies map.
     */
    private void computeLoopDependencies() {
        loopDependencies.clear();

        for (Loop loop : loops.values()) {
            Set<Stmt> body = loop.getStatements();

            // ensure each stmt has a deps set
            for (Stmt s : body) {
                loopDependencies.putIfAbsent(s, new HashSet<>());
            }

            // For each pair (defStmt, useStmt) inside the loop where def defines a value that use uses,
            // decide carried vs independent using heuristics:
            for (Stmt defStmt : body) {
                Set<Value> defVals = defUseAnalyzer.getDefValues(defStmt);
                if (defVals == null || defVals.isEmpty()) continue;

                for (Stmt useStmt : body) {
                    if (defStmt.equals(useStmt)) {
                        // same-statement case: may define and use same variable (e.g., x = x + i)
                        handleSelfStatementDeps(loop, defStmt);
                        continue;
                    }

                    Set<Value> useVals = defUseAnalyzer.getUseValues(useStmt);
                    if (useVals == null || useVals.isEmpty()) continue;

                    // intersection of defVals and useVals -> variables that flow from defStmt to useStmt
                    Set<Value> common = new HashSet<>(defVals);
                    common.retainAll(useVals);

                    for (Value v : common) {
                        // only consider if defStmt can reach useStmt (using reachingDefinitions)
                        Set<Stmt> reachingForUse = reachingDefinitions.get(useStmt);
                        if (reachingForUse != null && reachingForUse.contains(defStmt)) {
                            // decide carried vs independent
                            boolean carried = isLoopCarriedDependency(defStmt, useStmt, v, loop);
                            int distance = carried ? calculateDependencyDistance(defStmt, useStmt, v, loop) : 0;
                            LoopDependency ld = new LoopDependency(carried ? LoopDependency.Type.CARRIED : LoopDependency.Type.INDEPENDENT,
                                    v, distance, defStmt, useStmt, loop);
                            loopDependencies.get(useStmt).add(ld);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle the case where a single statement both defines and uses the same value,
     * e.g., x = x + i; This often produces a loop-carried dependency on x (value produced in
     * iteration k is used in iteration k+1), but we are conservative and test whether the
     * definition reaches the loop header (indicating it flows into next iteration).
     */
    private void handleSelfStatementDeps(Loop loop, Stmt stmt) {
        Set<Value> defs = defUseAnalyzer.getDefValues(stmt);
        Set<Value> uses = defUseAnalyzer.getUseValues(stmt);

        if (defs == null || uses == null) return;

        Set<Value> common = new HashSet<>(defs);
        common.retainAll(uses);
        if (common.isEmpty()) return;

        // if this statement's def reaches the loop header, that indicates the def flows to next iter
        Set<Stmt> reachingAtHeader = reachingDefinitions.get(loop.getHeader());
        boolean reachesHeader = reachingAtHeader != null && reachingAtHeader.contains(stmt);

        for (Value v : common) {
            LoopDependency.Type type = reachesHeader ? LoopDependency.Type.CARRIED : LoopDependency.Type.INDEPENDENT;
            int distance = type == LoopDependency.Type.CARRIED ? calculateDependencyDistance(stmt, stmt, v, loop) : 0;
            loopDependencies.get(stmt).add(new LoopDependency(type, v, distance, stmt, stmt, loop));
        }
    }

    private Value getArrayIndex(Stmt stmt, Value arrayVar) {
        for (Value use : defUseAnalyzer.getUseValues(stmt)) {
            if (use instanceof JArrayRef) {
                JArrayRef arrRef = (JArrayRef) use;
                if (arrRef.getBase().equals(arrayVar)) {
                    return arrRef.getIndex();
                }
            }
        }
        return null; // no array access
    }


    private boolean isArrayLoopCarried(Stmt defStmt, Stmt useStmt, Value arrayVar, Loop loop) {
        Value defIndex = getArrayIndex(defStmt, arrayVar);
        Value useIndex = getArrayIndex(useStmt, arrayVar);

        if (defIndex == null || useIndex == null) return false; // not an array or unknown

        // Check if indices are exactly equal
        if (defIndex.equals(useIndex)) {
            return false; // Loop-Independent (same iteration)
        }

        // Check simple shifts: i+1, i-1
        if (defIndex instanceof AbstractBinopExpr && useIndex instanceof Local) {
            AbstractBinopExpr binop = (AbstractBinopExpr) defIndex;
            if (binop.getOp1().equals(useIndex) || binop.getOp2().equals(useIndex)) {
                return true; // Loop-Carried (cross iteration)
            }
        }

        if (useIndex instanceof AbstractBinopExpr && defIndex instanceof Local) {
            AbstractBinopExpr binop = (AbstractBinopExpr) useIndex;
            if (binop.getOp1().equals(defIndex) || binop.getOp2().equals(defIndex)) {
                return true; // Loop-Carried
            }
        }

        // Default conservative: assume carried
        return true;
    }


    /**
     * Heuristic classification: returns true if we think this (def -> use) is loop-carried.
     *
     * Rules used (in roughly this priority):
     *  1) If defStmt == useStmt and def uses same var (x = x + ...): if def reaches header -> carried.
     *  2) If def reaches loop header (def is live at header) -> carried (value can flow to next iteration).
     *  3) If def does NOT dominate use (i.e., def may not happen before use in same iteration) -> carried.
     *  4) If def or use has array accesses, be conservative and treat as carried (unless more precise analysis added).
     *  5) If def is clearly an induction update (i = i + c) and the use consumes that updated value later in the same iteration, prefer independent.
     *  6) Otherwise, default conservative: carried.
     */
    private boolean isLoopCarriedDependency(Stmt defStmt, Stmt useStmt, Value variable, Loop loop) {
        // 1) Same-statement: if def and use are the same, check if it reaches the loop header.
        //    If it does, the value might flow to the next iteration → carried.
        if (defStmt.equals(useStmt)) {
            Set<Stmt> reachingAtHeader = reachingDefinitions.get(loop.getHeader());
            return reachingAtHeader != null && reachingAtHeader.contains(defStmt);
        }

        // 2) If the def reaches the loop header, it can flow to the next iteration → loop-carried.
        Set<Stmt> reachingAtHeader = reachingDefinitions.get(loop.getHeader());
        if (reachingAtHeader != null && reachingAtHeader.contains(defStmt)) {
            return true;
        }

        // 3) If def does not dominate use, it might only occur on some paths or after use in same iteration → carried.
        if (!dominatorAnalyzer.dominates(defStmt, useStmt)) {
            return true;
        }

        // 4) Array accesses: check indices for loop-carried or independent dependency.
        if (hasArrayAccess(defStmt) && hasArrayAccess(useStmt) && variable instanceof JArrayRef) {
            Value arrayBase = ((JArrayRef) variable).getBase();
            // Determine if dependency crosses iterations based on indices
            return isArrayLoopCarried(defStmt, useStmt, arrayBase, loop);
        }

        // 5) Induction variable: e.g., i = i + 1; if def dominates use, then value is seen in same iteration → independent
        if (isInductionVariable(defStmt, useStmt, variable)) {
            if (dominatorAnalyzer.dominates(defStmt, useStmt)) {
                return false; // independent (produced and used in same iteration)
            }
            return true; // conservative choice
        }

        // 6) Method calls or other unknown statements: conservatively assume carried
//        if (hasMethodCall(defStmt) || hasMethodCall(useStmt)) {
//            return true;
//        }

        // 7) Default: if both statements are in the loop but none of the above applied → independent
        if (loop.contains(defStmt) && loop.contains(useStmt)) {
            return false;
        }

        // 8) Safe conservative fallback: assume loop-carried
        return true;
    }


    /**
     * Very small heuristic for array presence: look at uses (reads) and defs (left-hand values)
     */
    private boolean hasArrayAccess(Stmt stmt) {
        Set<Value> uses = defUseAnalyzer.getUseValues(stmt);
        Set<Value> defs = defUseAnalyzer.getDefValues(stmt);
        if (uses != null) {
            for (Value v : uses) if (v instanceof JArrayRef) return true;
        }
        if (defs != null) {
            for (Value v : defs) if (v instanceof JArrayRef) return true;
        }
        return false;
    }

    /**
     * Heuristic: tries to detect induction updates like `i = i + K` or `i = i - K`.
     * Returns true when defStmt appears to increment/decrement the variable and useStmt actually uses it.
     */
    private boolean isInductionVariable(Stmt defStmt, Stmt useStmt, Value variable) {
        if (!(defStmt instanceof JAssignStmt)) return false;
        JAssignStmt assign = (JAssignStmt) defStmt;
        Value rhs = assign.getRightOp();
        if (!(rhs instanceof AbstractBinopExpr)) return false;

        AbstractBinopExpr bin = (AbstractBinopExpr) rhs;
        Value op1 = bin.getOp1();
        Value op2 = bin.getOp2();

        boolean looksLikeInc =
                (op1.equals(variable) && op2 instanceof IntConstant) ||
                        (op2.equals(variable) && op1 instanceof IntConstant);

        boolean useContains = defUseAnalyzer.getUseValues(useStmt) != null &&
                defUseAnalyzer.getUseValues(useStmt).contains(variable);

        return looksLikeInc && useContains;
    }

    /**
     * Primitive distance calculation: if defStmt is an induction update like i = i + C
     * return C; else default to 1 for carried deps. This is just a heuristic.
     */
    private int calculateDependencyDistance(Stmt defStmt, Stmt useStmt, Value variable, Loop loop) {
        if (!(defStmt instanceof JAssignStmt)) return 1;
        JAssignStmt assign = (JAssignStmt) defStmt;
        Value rhs = assign.getRightOp();
        if (rhs instanceof AbstractBinopExpr) {
            AbstractBinopExpr bin = (AbstractBinopExpr) rhs;
            Value op1 = bin.getOp1();
            Value op2 = bin.getOp2();

            if (op1.equals(variable) && op2 instanceof IntConstant) {
                return Math.abs(((IntConstant) op2).getValue());
            } else if (op2.equals(variable) && op1 instanceof IntConstant) {
                return Math.abs(((IntConstant) op1).getValue());
            }
        }
        // For x = x + i style, distance is commonly 1 (value flows to next iter)
        return 1;
    }

    // ---------- Utilities / public getters ----------

    public Map<Stmt, Loop> getLoops() {
        return Collections.unmodifiableMap(loops);
    }

    public boolean isInLoop(Stmt stmt) {
        for (Loop l : loops.values()) if (l.contains(stmt)) return true;
        return false;
    }

    public Loop getLoopForStatement(Stmt stmt) {
        for (Loop l : loops.values()) if (l.contains(stmt)) return l;
        return null;
    }

    /**
     * Return loop dependencies for a statement (may be empty)
     */
    public Set<LoopDependency> getLoopDependencies(Stmt stmt) {
        return Collections.unmodifiableSet(loopDependencies.getOrDefault(stmt, new HashSet<>()));
    }

    public void printLoopAnalysis() {
        System.out.println("\n=== LOOP ANALYSIS RESULTS ===");
        for (Loop loop : loops.values()) {
            printLoopInfo(loop, 0);
        }
    }

    private void printLoopInfo(Loop loop, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");

        System.out.println(indent + "Loop Header: " + loop.getHeader());
        System.out.println(indent + "Statements: " + loop.getStatements().size());

        for (Stmt s : loop.getStatements()) {
            Set<LoopDependency> deps = loopDependencies.get(s);
            if (deps != null && !deps.isEmpty()) {
                System.out.println(indent + "  - " + s + " [Dependencies: " + deps.size() + "]");
                for (LoopDependency ld : deps) {
                    System.out.println(indent + "     " + ld);
                }
            }
        }

        for (Loop nested : loop.getNestedLoops()) {
            printLoopInfo(nested, depth + 1);
        }
    }
}
