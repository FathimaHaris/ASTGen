package org.example.analyzer.dependency;

import sootup.core.jimple.common.ref.JArrayRef;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.expr.*;
import sootup.core.jimple.common.ref.JInstanceFieldRef;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.graph.StmtGraph;
import java.util.*;
import sootup.core.jimple.common.expr.AbstractBinopExpr;

public class DefUseAnalyzer {
    private StmtGraph<?> cfg;
    private Map<Stmt, Set<String>> defSets;
    private Map<Stmt, Set<String>> useSets;

    public DefUseAnalyzer(StmtGraph<?> cfg) {
        this.cfg = cfg;
        this.defSets = new HashMap<>();
        this.useSets = new HashMap<>();
        analyzeDefUse();
    }

    private void analyzeDefUse() {
        for (Object node : cfg.getNodes()) {
            if (node instanceof Stmt) {
                Stmt stmt = (Stmt) node;
                defSets.put(stmt, new HashSet<>());
                useSets.put(stmt, new HashSet<>());
                analyzeStatement(stmt);
            }
        }
    }

    private void analyzeStatement(Stmt stmt) {
        // Get defined variables (left-hand side)
        Set<String> defs = getDefinedVariables(stmt);
        defSets.get(stmt).addAll(defs);

        // Get used variables (right-hand side)
        Set<String> uses = getUsedVariables(stmt);
        useSets.get(stmt).addAll(uses);
    }

    private Set<String> getDefinedVariables(Stmt stmt) {
        Set<String> defs = new HashSet<>();

        // Handle different statement types
        if (stmt instanceof JAssignStmt) {
            Value lhs = ((JAssignStmt) stmt).getLeftOp();
            if (lhs instanceof Local) {
                defs.add(((Local) lhs).getName());
            }
        }


        return defs;
    }

    private Set<String> getUsedVariables(Stmt stmt) {
        Set<String> uses = new HashSet<>();
        stmt.getUses().forEach(value -> {
            if (value instanceof Local) {
                uses.add(((Local) value).getName());
            }
            findVariablesInValue(value, uses);
        });


        return uses;
    }

    private void findVariablesInValue(Value value, Set<String> variables) {
        if (value instanceof Local) {
            variables.add(((Local) value).getName());
        } else if (value instanceof AbstractBinopExpr) {
            AbstractBinopExpr binop = (AbstractBinopExpr) value;
            findVariablesInValue(binop.getOp1(), variables);
            findVariablesInValue(binop.getOp2(), variables);


    } else if (value instanceof JInstanceFieldRef) {
            JInstanceFieldRef fieldRef = (JInstanceFieldRef) value;
            findVariablesInValue(fieldRef.getBase(), variables);
        }

        // Add array support
        else if (value instanceof JArrayRef) {
            JArrayRef arrayRef = (JArrayRef) value;
            findVariablesInValue(arrayRef.getBase(), variables);  // Array variable
            findVariablesInValue(arrayRef.getIndex(), variables); // Index variable
        }

// Add method call support
//        if (value instanceof JInvokeStmt) {
//            JInvokeStmt invoke = (JInvokeStmt) value;
//            invoke.getArgs().forEach(arg -> findVariablesInValue(arg, uses));
//        }
        // Add more expression types as needed
    }

    // Getters
    public Set<String> getDefSet(Stmt stmt) { return defSets.get(stmt); }
    public Set<String> getUseSet(Stmt stmt) { return useSets.get(stmt); }
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