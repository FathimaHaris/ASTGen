package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.basic.Value;
import sootup.core.model.Body;
import sootup.core.types.PrimitiveType;
import sootup.core.jimple.common.constant.IntConstant;
import java.util.*;

public class LoopAnalyzer {
    private StmtGraph<?> cfg;
    private DominatorAnalyzer dominatorAnalyzer;
    private DefUseAnalyzer defUseAnalyzer;
    private Map<Stmt, Set<Stmt>> reachingDefinitions;
    private Map<Stmt, Loop> loops;
    private Map<Stmt, Set<LoopDependency>> loopDependencies;

    public LoopAnalyzer(StmtGraph<?> cfg, DominatorAnalyzer dominatorAnalyzer,
                        DefUseAnalyzer defUseAnalyzer, Map<Stmt, Set<Stmt>> reachingDefinitions) {
        this.cfg = cfg;
        this.dominatorAnalyzer = dominatorAnalyzer;
        this.defUseAnalyzer = defUseAnalyzer;
        this.reachingDefinitions = reachingDefinitions;
        this.loops = new HashMap<>();
        this.loopDependencies = new HashMap<>();
        analyze();
    }

    private void analyze() {
        findNaturalLoops();
        resolveNestedLoops();
        analyzeLoopDependencies();
    }

    private void findNaturalLoops() {
        for (Stmt source : cfg.getStmts()) {
            for (Stmt target : cfg.successors(source)) {
                if (dominatorAnalyzer.dominates(target, source)) {
                    Loop loop = loops.computeIfAbsent(target, k -> new Loop(target));
                    findLoopBody(loop, source);
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

                for (Stmt pred : cfg.predecessors(current)) {
                    if (!pred.equals(loop.getHeader())) {
                        worklist.push(pred);
                    }
                }
            }
        }
    }

    private void resolveNestedLoops() {
        List<Loop> loopList = new ArrayList<>(loops.values());

        for (int i = 0; i < loopList.size(); i++) {
            for (int j = i + 1; j < loopList.size(); j++) {
                Loop loop1 = loopList.get(i);
                Loop loop2 = loopList.get(j);

                if (isNested(loop1, loop2)) {
                    loop1.addNestedLoop(loop2);
                } else if (isNested(loop2, loop1)) {
                    loop2.addNestedLoop(loop1);
                }
            }
        }
    }

    private boolean isNested(Loop outer, Loop inner) {
        if (outer.equals(inner)) return false;

        for (Stmt stmt : inner.getStatements()) {
            if (!outer.contains(stmt)) return false;
        }
        return true;
    }

    private void analyzeLoopDependencies() {
        for (Loop loop : loops.values()) {
            analyzeDependenciesInLoop(loop);
        }
    }

    private void analyzeDependenciesInLoop(Loop loop) {
        Set<Stmt> loopStatements = loop.getStatements();

        for (Stmt stmt : loopStatements) {
            loopDependencies.putIfAbsent(stmt, new HashSet<>());
            analyzeDataDependenciesInLoop(stmt, loop, loopStatements);
        }
    }

    private void analyzeDataDependenciesInLoop(Stmt stmt, Loop loop, Set<Stmt> loopStatements) {
        Set<Value> uses = defUseAnalyzer.getUseValues(stmt);

        for (Value usedValue : uses) {
            for (Stmt defStmt : reachingDefinitions.get(stmt)) {
                if (loopStatements.contains(defStmt)) {
                    Set<Value> defs = defUseAnalyzer.getDefValues(defStmt);

                    if (defs.contains(usedValue)) {
                        analyzeLoopDependencyType(defStmt, stmt, usedValue, loop);
                    }
                }
            }
        }
    }

    private void analyzeLoopDependencyType(Stmt defStmt, Stmt useStmt, Value variable, Loop loop) {
        LoopDependency.Type type = isLoopCarriedDependency(defStmt, useStmt, variable, loop) ?
                LoopDependency.Type.CARRIED : LoopDependency.Type.INDEPENDENT;

        int distance = type == LoopDependency.Type.CARRIED ?
                calculateDependencyDistance(defStmt, useStmt, variable, loop) : 0;

        LoopDependency dep = new LoopDependency(type, variable, distance, defStmt, useStmt, loop);
        loopDependencies.get(useStmt).add(dep);
    }

    private boolean isLoopCarriedDependency(Stmt defStmt, Stmt useStmt, Value variable, Loop loop) {
        // Check if the dependency crosses iterations
        if (hasArrayAccess(defStmt) && hasArrayAccess(useStmt)) {
            return true;
        }

        if (isInductionVariable(defStmt, useStmt, variable)) {
            return true;
        }

        if (hasMethodCall(defStmt) || hasMethodCall(useStmt)) {
            return true;
        }

        if (loop.contains(defStmt) && loop.contains(useStmt)) {
            return false; // independent dependency
        }

        // Default: assume carried dependency for safety
        return true;
    }

    private boolean hasArrayAccess(Stmt stmt) {
        for (Value value : defUseAnalyzer.getUseValues(stmt)) {
            if (value instanceof JArrayRef) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMethodCall(Stmt stmt) {
        if (stmt instanceof JAssignStmt) {
            JAssignStmt assignStmt = (JAssignStmt) stmt;
            Value rightOp = assignStmt.getRightOp();
            return rightOp instanceof AbstractInvokeExpr;
        }
        return stmt instanceof JInvokeStmt;
    }

    private boolean isInductionVariable(Stmt defStmt, Stmt useStmt, Value variable) {
        if (!(defStmt instanceof JAssignStmt)) return false;

        JAssignStmt assignStmt = (JAssignStmt) defStmt;
        Value rightOp = assignStmt.getRightOp();

        if (!(rightOp instanceof AbstractBinopExpr)) return false;

        AbstractBinopExpr binop = (AbstractBinopExpr) rightOp;
        Value op1 = binop.getOp1();
        Value op2 = binop.getOp2();

        boolean isIncrement = (op1.equals(variable) && op2 instanceof IntConstant) ||
                (op2.equals(variable) && op1 instanceof IntConstant);

        boolean isUsed = defUseAnalyzer.getUseValues(useStmt).contains(variable);

        return isIncrement && isUsed;
    }

    private int calculateDependencyDistance(Stmt defStmt, Stmt useStmt, Value variable, Loop loop) {
        if (!(defStmt instanceof JAssignStmt)) return 1;

        JAssignStmt assignStmt = (JAssignStmt) defStmt;
        Value rightOp = assignStmt.getRightOp();

        if (rightOp instanceof AbstractBinopExpr) {
            AbstractBinopExpr binop = (AbstractBinopExpr) rightOp;
            Value op1 = binop.getOp1();
            Value op2 = binop.getOp2();

            if (op1.equals(variable) && op2 instanceof IntConstant) {
                return ((IntConstant) op2).getValue();
            } else if (op2.equals(variable) && op1 instanceof IntConstant) {
                return ((IntConstant) op1).getValue();
            }
        }

        return 1;
    }

    // Public API
    public Map<Stmt, Loop> getLoops() {
        return Collections.unmodifiableMap(loops);
    }

    public boolean isInLoop(Stmt stmt) {
        for (Loop loop : loops.values()) {
            if (loop.contains(stmt)) return true;
        }
        return false;
    }

    public Loop getLoopForStatement(Stmt stmt) {
        for (Loop loop : loops.values()) {
            if (loop.contains(stmt)) return loop;
        }
        return null;
    }

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

        for (Stmt stmt : loop.getStatements()) {
            Set<LoopDependency> deps = loopDependencies.get(stmt);
            if (deps != null && !deps.isEmpty()) {
                System.out.println(indent + "  - " + stmt + " [Dependencies: " + deps.size() + "]");
            }
        }

        for (Loop nested : loop.getNestedLoops()) {
            printLoopInfo(nested, depth + 1);
        }
    }
}