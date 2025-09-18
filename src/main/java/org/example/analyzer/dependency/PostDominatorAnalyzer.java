package org.example.analyzer.dependency;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;

import java.util.*;

/**
 * PostDominatorAnalyzer - computes post-dominators for a CFG built from StmtGraph.
 *
 * Algorithm: iterative dataflow (dual of dominators):
 *   postdom(exit) = { exit }
 *   postdom(n) = { n } ∪ ⋂ postdom(s) for all successors s of n
 *
 *
 */
public class PostDominatorAnalyzer {
    private StmtGraph<?> cfg;
    private Map<Stmt, Set<Stmt>> postDominators;
    private Map<Stmt, Stmt> immediatePostDominators;
    private Stmt exitStatement;

    public PostDominatorAnalyzer(StmtGraph<?> cfg) {
        this.cfg = cfg;
        this.postDominators = new HashMap<>();
        this.immediatePostDominators = new HashMap<>();
        analyze();
    }

    private void analyze() {
        findExitStatement();
        initializePostDominators();
        computePostDominators();
        computeImmediatePostDominators();
    }

    private void findExitStatement() {
        // find a node with no successors - treat it as exit
        for (Stmt stmt : cfg.getStmts()) {
            if (cfg.successors(stmt).isEmpty()) {
                exitStatement = stmt;
                return;
            }
        }
        // fallback: use last statement if no explicit exit
        Iterator<Stmt> it = cfg.getStmts().iterator();
        Stmt last = null;
        while (it.hasNext()) last = it.next();
        exitStatement = last;
    }

    private void initializePostDominators() {
        Set<Stmt> all = getAllStatements();
        for (Stmt stmt : all) {
            if (stmt.equals(exitStatement)) {
                postDominators.put(stmt, new HashSet<>(Set.of(stmt)));
            } else {
                postDominators.put(stmt, new HashSet<>(all));
            }
        }
    }

    private void computePostDominators() {
        boolean changed;
        int iteration = 0;
        // We can iterate until fixpoint
        do {
            changed = false;
            iteration++;

            for (Stmt stmt : getAllStatements()) {
                if (stmt.equals(exitStatement)) continue;

                // intersection of postdoms of all successors
                Set<Stmt> newSet = null;
                List<Stmt> succs = new ArrayList<>(cfg.successors(stmt));

                if (succs.isEmpty()) {
                    // If a node has no successors (shouldn't happen except exit),
                    // then its postdoms is itself
                    newSet = new HashSet<>();
                } else {
                    // start with a copy of the first successor's postdoms
                    newSet = new HashSet<>(postDominators.get(succs.get(0)));
                    for (int i = 1; i < succs.size(); i++) {
                        newSet.retainAll(postDominators.get(succs.get(i)));
                    }
                }

                // Add the node itself
                newSet.add(stmt);

                if (!newSet.equals(postDominators.get(stmt))) {
                    postDominators.put(stmt, new HashSet<>(newSet));
                    changed = true;
                }
            }
        } while (changed && iteration < 1000);
    }

    private void computeImmediatePostDominators() {
        for (Stmt stmt : getAllStatements()) {
            if (stmt.equals(exitStatement)) {
                immediatePostDominators.put(stmt, null);
                continue;
            }

            Set<Stmt> pdoms = new HashSet<>(postDominators.get(stmt));
            pdoms.remove(stmt);

            // immediate postdom is the one in pdoms that is not post-dominated by any other
            Stmt ipdom = null;
            for (Stmt candidate : pdoms) {
                if (ipdom == null) {
                    ipdom = candidate;
                } else if (postDominators.get(candidate).contains(ipdom)) {
                    ipdom = candidate;
                }
            }

            immediatePostDominators.put(stmt, ipdom);
        }
    }

    private Set<Stmt> getAllStatements() {
        return new HashSet<>(cfg.getStmts());
    }

    // Public API
    public Set<Stmt> getPostDominators(Stmt stmt) {
        return Collections.unmodifiableSet(postDominators.getOrDefault(stmt, new HashSet<>()));
    }

    public Stmt getImmediatePostDominator(Stmt stmt) {
        return immediatePostDominators.get(stmt);
    }

    public boolean postDominates(Stmt a, Stmt b) {
        // does a post-dominate b? i.e. is a in postDominators[b]
        Set<Stmt> pdoms = postDominators.get(b);
        return pdoms != null && pdoms.contains(a);
    }

    public Stmt getExitStatement() {
        return exitStatement;
    }

    public void printPostDominators() {
        System.out.println("\n=== POST-DOMINATOR ANALYSIS RESULTS ===");
        System.out.println("Exit statement: " + exitStatement);

        for (Stmt stmt : getAllStatements()) {
            System.out.println("\nStatement: " + stmt);
            Set<Stmt> pdomSet = postDominators.get(stmt);
            System.out.println("  PostDominators: " + (pdomSet != null ? pdomSet.size() : 0) + " statements");
            Stmt ipdom = immediatePostDominators.get(stmt);
            System.out.println("  Immediate post-dominator: " + (ipdom != null ? ipdom : "EXIT"));
        }
    }

    public void printPostDominatorTree() {
        System.out.println("\n=== POST-DOMINATOR TREE ===");
        printPostDominatorTreeRecursive(exitStatement, 0);
    }

    private void printPostDominatorTreeRecursive(Stmt stmt, int depth) {
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) indent.append("  ");

        System.out.println(indent + "└─ " + stmt);

        for (Stmt child : getAllStatements()) {
            if (stmt.equals(immediatePostDominators.get(child))) {
                printPostDominatorTreeRecursive(child, depth + 1);
            }
        }
    }
}
