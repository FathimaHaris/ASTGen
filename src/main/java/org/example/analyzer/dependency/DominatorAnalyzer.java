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
        for (Object node : cfg.getNodes()) {
            if (node instanceof Stmt) {
                Stmt stmt = (Stmt) node;
                if (cfg.predecessors(stmt).isEmpty()) {
                    entryStatement = stmt;
                    return;
                }
            }
        }
        // If no entry found, use first statement
        for (Object node : cfg.getNodes()) {
            if (node instanceof Stmt) {
                entryStatement = (Stmt) node;
                return;
            }
        }
    }

    private void initializeDominators() {
        // Initialize each statement to dominate all statements
        Set<Stmt> allStatements = getAllStatements();

        for (Stmt stmt : allStatements) {
            if (stmt.equals(entryStatement)) {
                // Entry dominates only itself initially
                dominators.put(stmt, new HashSet<>(Set.of(stmt)));//Set.of immutable set with specific elemt
            } else {
                // Others dominate all statements initially
                dominators.put(stmt, new HashSet<>(allStatements));
            }
        }
    }

    private void computeDominators() {
        boolean changed;
        int iteration = 0;

        do {
            changed = false;
            iteration++;
            System.out.println("Dominator iteration: " + iteration);

            List<Stmt> reversePostOrder = getReversePostOrder();

            for (Stmt stmt : reversePostOrder) {
                if (stmt.equals(entryStatement)) {
                    continue;
                }

                // Get CURRENT dominators (unchanged during this iteration)
                Set<Stmt> currentDominators = dominators.get(stmt);

                // Calculate NEW dominators
                Set<Stmt> newDominators = new HashSet<>(getAllStatements()); // Start with all

                for (Object predObj : cfg.predecessors(stmt)) {
                    if (predObj instanceof Stmt) {
                        Stmt pred = (Stmt) predObj;
                        newDominators.retainAll(dominators.get(pred)); // Intersection
                    }
                }

                newDominators.add(stmt); // Union with self

                // Check if changed
                if (!newDominators.equals(currentDominators)) {
                    // Create a NEW set to avoid modification issues
                    dominators.put(stmt, new HashSet<>(newDominators));
                    changed = true;
                }
            }

        } while (changed && iteration < 100);
    }


    private void computeImmediateDominators() {
        for (Stmt stmt : getAllStatements()) {
            if (stmt.equals(entryStatement)) {
                immediateDominators.put(stmt, null); // Entry has no immediate dominator
                continue;
            }

            // Find the immediate dominator (closest dominator)
            Set<Stmt> stmtDominators = new HashSet<>(dominators.get(stmt));
            stmtDominators.remove(stmt); // Remove self

            Stmt immediateDom = null;
            for (Stmt dom : stmtDominators) {
                if (immediateDom == null) {
                    immediateDom = dom;
                } else {
                    // Check if dom is closer than current immediateDom
                    if (dominators.get(dom).contains(immediateDom)) {
                        immediateDom = dom; // dom is closer
                    }
                }
            }

            immediateDominators.put(stmt, immediateDom);
        }
    }

    private Set<Stmt> getAllStatements() {
        Set<Stmt> statements = new HashSet<>();
        for (Object node : cfg.getNodes()) {
            if (node instanceof Stmt) {
                statements.add((Stmt) node);
            }
        }
        return statements;
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

        for (Object succObj : cfg.successors(current)) {
            if (succObj instanceof Stmt) {
                Stmt succ = (Stmt) succObj;
                if (!visited.contains(succ)) {
                    dfsPostOrder(succ, visited, order);
                }
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
            System.out.println("  Immediate dominator: " +
                    (immDom != null ? immDom : "ENTRY"));

            // Print dominators list
            for (Stmt dom : dominators.get(stmt)) {
                System.out.println("    - " + dom);
            }
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

        // Find children (statements that this stmt immediately dominates)
        for (Stmt child : getAllStatements()) {
            if (immediateDominators.get(child) == stmt) {
                printDominatorTreeRecursive(child, depth + 1);
            }
        }
    }
}