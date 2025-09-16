package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import java.util.*;

public class DominatorAnalyzer {
    private StmtGraph<?> cfg;
    private Map<Stmt, Set<Stmt>> dominators;
    private Map<Stmt, Stmt> immediateDominators;
    private Stmt entryStatement;

    public DominatorAnalyzer(StmtGraph<?> cfg) {
        this.cfg = cfg;
        this.dominators = new HashMap<>();
        this.immediateDominators = new HashMap<>();
        analyze();
    }

    private void analyze() {
        findEntryStatement();
        initializeDominators();
        computeDominators();
        computeImmediateDominators();
    }

    private void findEntryStatement() {
        for (Stmt stmt : cfg.getStmts()) {
            if (cfg.predecessors(stmt).isEmpty()) {
                entryStatement = stmt;
                return;
            }
        }
        // If no entry found, use first statement
        Iterator<Stmt> stmtIterator = cfg.getStmts().iterator();
        if (stmtIterator.hasNext()) {
            entryStatement = stmtIterator.next();
        }
    }

    private void initializeDominators() {
        Set<Stmt> allStatements = getAllStatements();

        for (Stmt stmt : allStatements) {
            if (stmt.equals(entryStatement)) {
                dominators.put(stmt, new HashSet<>(Set.of(stmt)));
            } else {
                dominators.put(stmt, new HashSet<>(allStatements));
            }
        }
    }

    private void computeDominators() {
        boolean changed;
        int iteration = 0;
        List<Stmt> reversePostOrder = getReversePostOrder();

        do {
            changed = false;
            iteration++;

            for (Stmt stmt : reversePostOrder) {
                if (stmt.equals(entryStatement)) {
                    continue;
                }

                Set<Stmt> newDominators = new HashSet<>(getAllStatements());

                for (Stmt pred : cfg.predecessors(stmt)) {
                    newDominators.retainAll(dominators.get(pred));
                }

                newDominators.add(stmt);

                if (!newDominators.equals(dominators.get(stmt))) {
                    dominators.put(stmt, new HashSet<>(newDominators));
                    changed = true;
                }
            }
        } while (changed && iteration < 100);
    }

    private void computeImmediateDominators() {
        for (Stmt stmt : getAllStatements()) {
            if (stmt.equals(entryStatement)) {
                immediateDominators.put(stmt, null);
                continue;
            }

            Set<Stmt> stmtDominators = new HashSet<>(dominators.get(stmt));
            stmtDominators.remove(stmt);

            Stmt immediateDom = null;
            for (Stmt dom : stmtDominators) {
                if (immediateDom == null) {
                    immediateDom = dom;
                } else if (dominators.get(dom).contains(immediateDom)) {
                    immediateDom = dom;
                }
            }

            immediateDominators.put(stmt, immediateDom);
        }
    }

    private Set<Stmt> getAllStatements() {
        return new HashSet<>(cfg.getStmts());
    }

    private List<Stmt> getReversePostOrder() {
        List<Stmt> reversePostOrder = new ArrayList<>();
        Set<Stmt> visited = new HashSet<>();
        dfsPostOrder(entryStatement, visited, reversePostOrder);
        Collections.reverse(reversePostOrder);
        return reversePostOrder;
    }

    private void dfsPostOrder(Stmt current, Set<Stmt> visited, List<Stmt> order) {
        visited.add(current);

        for (Stmt succ : cfg.successors(current)) {
            if (!visited.contains(succ)) {
                dfsPostOrder(succ, visited, order);
            }
        }

        order.add(current);
    }

    // Public API
    public Set<Stmt> getDominators(Stmt stmt) {
        return Collections.unmodifiableSet(dominators.getOrDefault(stmt, new HashSet<>()));
    }

    public Stmt getImmediateDominator(Stmt stmt) {
        return immediateDominators.get(stmt);
    }

    public boolean dominates(Stmt dominator, Stmt dominated) {
        return dominators.get(dominated).contains(dominator);
    }

    public Stmt getEntryStatement() {
        return entryStatement;
    }

    public void printDominators() {
        System.out.println("\n=== DOMINATOR ANALYSIS RESULTS ===");
        System.out.println("Entry statement: " + entryStatement);

        for (Stmt stmt : getAllStatements()) {
            System.out.println("\nStatement: " + stmt);
            System.out.println("  Dominators: " + dominators.get(stmt).size() + " statements");

            Stmt immDom = immediateDominators.get(stmt);
            System.out.println("  Immediate dominator: " + (immDom != null ? immDom : "ENTRY"));
        }
    }

    public void printDominatorTree() {
        System.out.println("\n=== DOMINATOR TREE ===");
        printDominatorTreeRecursive(entryStatement, 0);
    }

    private void printDominatorTreeRecursive(Stmt stmt, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }

        System.out.println(indent + "└─ " + stmt);

        for (Stmt child : getAllStatements()) {
            if (stmt.equals(immediateDominators.get(child))) {
                printDominatorTreeRecursive(child, depth + 1);
            }
        }
    }
}