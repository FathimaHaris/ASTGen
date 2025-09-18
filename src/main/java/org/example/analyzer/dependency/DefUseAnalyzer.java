package org.example.analyzer.dependency;

import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.graph.StmtGraph;
import java.util.*;

public class DefUseAnalyzer {
    private StmtGraph<?> cfg;
    private Map<Stmt, Set<String>> defSets;
    private Map<Stmt, Set<String>> useSets;
    private Map<Stmt, Set<Value>> defValues;
    private Map<Stmt, Set<Value>> useValues;

    public DefUseAnalyzer(StmtGraph<?> cfg) {
        this.cfg = cfg;
        this.defSets = new HashMap<>();
        this.useSets = new HashMap<>();
        this.defValues = new HashMap<>();
        this.useValues = new HashMap<>();
        analyzeDefUse();
    }

    private void analyzeDefUse() {
        for (Stmt stmt : cfg.getStmts()) {
            defSets.put(stmt, new HashSet<>());
            useSets.put(stmt, new HashSet<>());
            defValues.put(stmt, new HashSet<>());
            useValues.put(stmt, new HashSet<>());
            analyzeStatement(stmt);
        }
    }

    private void analyzeStatement(Stmt stmt) {
        // Get defined variables
        Set<String> defs = getDefinedVariables(stmt);
        defSets.get(stmt).addAll(defs);

        // Get defined values
        Set<Value> defVals = getDefinedValues(stmt);
        defValues.get(stmt).addAll(defVals);

        // Get used variables
        Set<String> uses = getUsedVariables(stmt);
        useSets.get(stmt).addAll(uses);

        // Get used values
        Set<Value> useVals = getUsedValues(stmt);
        useValues.get(stmt).addAll(useVals);
    }

    private Set<String> getDefinedVariables(Stmt stmt) {
        Set<String> defs = new HashSet<>();

        if (stmt instanceof JAssignStmt) {
            JAssignStmt assignStmt = (JAssignStmt) stmt;
            Value lhs = assignStmt.getLeftOp();
            extractVariablesFromValue(lhs, defs, true);
        } else if (stmt instanceof JIdentityStmt) {
            JIdentityStmt identityStmt = (JIdentityStmt) stmt;
            Value lhs = identityStmt.getLeftOp();
            if (lhs instanceof Local) {
                defs.add(((Local) lhs).getName());
            }
        }

        return defs;
    }

    private Set<Value> getDefinedValues(Stmt stmt) {
        Set<Value> defVals = new HashSet<>();

        if (stmt instanceof JAssignStmt) {
            JAssignStmt assignStmt = (JAssignStmt) stmt;
            defVals.add(assignStmt.getLeftOp());
        } else if (stmt instanceof JIdentityStmt) {
            JIdentityStmt identityStmt = (JIdentityStmt) stmt;
            defVals.add(identityStmt.getLeftOp());
        }

        return defVals;
    }

    private Set<String> getUsedVariables(Stmt stmt) {
        Set<String> uses = new HashSet<>();

        for (Value value : stmt.getUses()) {
            extractVariablesFromValue(value, uses, false);
        }

        return uses;
    }

    private Set<Value> getUsedValues(Stmt stmt) {
        return new HashSet<>(stmt.getUses());
    }

    private void extractVariablesFromValue(Value value, Set<String> variables, boolean isDefinition) {
        if (value instanceof Local) {
            variables.add(((Local) value).getName());
        } else if (value instanceof AbstractBinopExpr) {
            AbstractBinopExpr binop = (AbstractBinopExpr) value;
            extractVariablesFromValue(binop.getOp1(), variables, false);
            extractVariablesFromValue(binop.getOp2(), variables, false);
        } else if (value instanceof JInstanceFieldRef) {
            JInstanceFieldRef fieldRef = (JInstanceFieldRef) value;
            extractVariablesFromValue(fieldRef.getBase(), variables, false);
        } else if (value instanceof JArrayRef) {
            JArrayRef arrayRef = (JArrayRef) value;
            extractVariablesFromValue(arrayRef.getBase(), variables, isDefinition);
            extractVariablesFromValue(arrayRef.getIndex(), variables, false);
        } else if (value instanceof JCastExpr) {
            JCastExpr castExpr = (JCastExpr) value;
            extractVariablesFromValue(castExpr.getOp(), variables, false);
        } else if (value instanceof JLengthExpr) {
            JLengthExpr lengthExpr = (JLengthExpr) value;
            extractVariablesFromValue(lengthExpr.getOp(), variables, false);
        } else if (value instanceof JNewExpr || value instanceof JNewArrayExpr ||
                value instanceof JNewMultiArrayExpr) {
            // These create new objects, no variables to extract for use
        } else if (value instanceof AbstractInvokeExpr) {
            AbstractInvokeExpr invokeExpr = (AbstractInvokeExpr) value;
            for (Value arg : invokeExpr.getArgs()) {
                extractVariablesFromValue(arg, variables, false);
            }
            if (invokeExpr instanceof JInstanceInvokeExpr) {
                JInstanceInvokeExpr instanceInvoke = (JInstanceInvokeExpr) invokeExpr;
                extractVariablesFromValue(instanceInvoke.getBase(), variables, false);
            }
        }
    }

    // Getters
    public Set<String> getDefSet(Stmt stmt) { return defSets.get(stmt); }
    public Set<String> getUseSet(Stmt stmt) { return useSets.get(stmt); }
    public Set<Value> getDefValues(Stmt stmt) { return defValues.get(stmt); }
    public Set<Value> getUseValues(Stmt stmt) { return useValues.get(stmt); }
    public Map<Stmt, Set<String>> getAllDefSets() { return defSets; }
    public Map<Stmt, Set<String>> getAllUseSets() { return useSets; }

    public void printDefUseSets() {
        System.out.println("=== DEF/USE SETS ===");
        for (Stmt stmt : defSets.keySet()) {
            System.out.println("Stmt: " + stmt);
            System.out.println("  DEF: " + defSets.get(stmt));
            System.out.println("  USE: " + useSets.get(stmt));
        }
    }
}