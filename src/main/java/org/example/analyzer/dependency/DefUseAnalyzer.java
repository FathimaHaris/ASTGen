    package org.example.analyzer.dependency;

    import sootup.core.jimple.common.ref.JArrayRef;
    import sootup.core.jimple.common.ref.JInstanceFieldRef;
    import sootup.core.jimple.common.expr.*;
    import sootup.core.jimple.common.stmt.*;
    import sootup.core.jimple.basic.Local;
    import sootup.core.jimple.basic.Value;
    import sootup.core.graph.StmtGraph;
    import java.util.*;
    import java.util.stream.Collectors;

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
            // cfg.getStmts() returns a List, use it directly
            List<Stmt> stmts = cfg.getStmts();

            for (Stmt stmt : stmts) {
                defSets.put(stmt, new HashSet<String>());
                useSets.put(stmt, new HashSet<String>());
                defValues.put(stmt, new HashSet<Value>());
                useValues.put(stmt, new HashSet<Value>());
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
            Set<String> defs = new HashSet<String>();

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
            Set<Value> defVals = new HashSet<Value>();

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
            Set<String> uses = new HashSet<String>();

            // stmt.getUses() returns a Stream, convert to List using forEach
            stmt.getUses().forEach(value -> {
                extractVariablesFromValue(value, uses, false);
            });

            return uses;
        }

        private Set<Value> getUsedValues(Stmt stmt) {
            // Convert Stream to Set using collect
            return stmt.getUses().collect(java.util.stream.Collectors.toSet());
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
            }else if (value instanceof AbstractInvokeExpr) {
                AbstractInvokeExpr invokeExpr = (AbstractInvokeExpr) value;

                // invokeExpr.getArgs() returns a Stream, use forEach
                invokeExpr.getArgs().forEach(arg -> {
                    extractVariablesFromValue(arg, variables, false);
                });

                // Handle instance method calls
                if (invokeExpr instanceof JSpecialInvokeExpr) {
                    JSpecialInvokeExpr specialInvoke = (JSpecialInvokeExpr) invokeExpr;
                    extractVariablesFromValue(specialInvoke.getBase(), variables, false);
                } else if (invokeExpr instanceof JVirtualInvokeExpr) {
                    JVirtualInvokeExpr virtualInvoke = (JVirtualInvokeExpr) invokeExpr;
                    extractVariablesFromValue(virtualInvoke.getBase(), variables, false);
                } else if (invokeExpr instanceof JInterfaceInvokeExpr) {
                    JInterfaceInvokeExpr interfaceInvoke = (JInterfaceInvokeExpr) invokeExpr;
                    extractVariablesFromValue(interfaceInvoke.getBase(), variables, false);
                }
            }
        }

        // Getters
        public Set<String> getDefSet(Stmt stmt) {
            return defSets.getOrDefault(stmt, new HashSet<String>());
        }

        public Set<String> getUseSet(Stmt stmt) {
            return useSets.getOrDefault(stmt, new HashSet<String>());
        }

        public Set<Value> getDefValues(Stmt stmt) {
            return defValues.getOrDefault(stmt, new HashSet<Value>());
        }

        public Set<Value> getUseValues(Stmt stmt) {
            return useValues.getOrDefault(stmt, new HashSet<Value>());
        }

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