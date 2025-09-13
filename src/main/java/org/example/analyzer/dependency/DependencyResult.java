package org.example.analyzer.dependency;

import sootup.core.jimple.common.stmt.Stmt;
import java.util.*;

public class DependencyResult {
    private Map<Stmt, Set<Dependency>> dataDependencies;
    private Map<Stmt, Set<Dependency>> controlDependencies;
    private Map<Stmt, Set<LoopDependency>> loopDependencies;

    public DependencyResult() {
        this.dataDependencies = new HashMap<>();
        this.controlDependencies = new HashMap<>();
        this.loopDependencies = new HashMap<>();
    }

    // Add dependencies
    public void addDataDependency(Dependency dep) {
        dataDependencies.computeIfAbsent(dep.getTarget(), k -> new HashSet<>()).add(dep);
    }

    public void addControlDependency(Stmt controlled, Stmt controller) {
        Dependency dep = new Dependency(Dependency.Type.CONTROL, controller, controlled, null);
        controlDependencies.computeIfAbsent(controlled, k -> new HashSet<>()).add(dep);
    }

    public void addLoopDependency(Stmt stmt, LoopDependency loopDep) {
        loopDependencies.computeIfAbsent(stmt, k -> new HashSet<>()).add(loopDep);
    }

    // Getters
    public Map<Stmt, Set<Dependency>> getDataDependencies() { return dataDependencies; }
    public Map<Stmt, Set<Dependency>> getControlDependencies() { return controlDependencies; }
    public Map<Stmt, Set<LoopDependency>> getLoopDependencies() { return loopDependencies; }

    // Utility methods
    public Set<Dependency> getAllDependenciesForStmt(Stmt stmt) {
        Set<Dependency> allDeps = new HashSet<>();
        if (dataDependencies.containsKey(stmt)) {
            allDeps.addAll(dataDependencies.get(stmt));
        }
        if (controlDependencies.containsKey(stmt)) {
            allDeps.addAll(controlDependencies.get(stmt));
        }
        return allDeps;
    }

    public void printResults() {
        System.out.println("=== DETAILED DEPENDENCY ANALYSIS RESULTS ===");

        System.out.println("\nData Dependencies (RAW, WAR, WAW):");
        dataDependencies.forEach((stmt, deps) -> {
            if (!deps.isEmpty()) {
                System.out.println("\nStmt: " + stmt);
                deps.forEach(dep -> System.out.println("  " + dep));
            }
        });

        System.out.println("\nControl Dependencies:");
        controlDependencies.forEach((stmt, deps) -> {
            if (!deps.isEmpty()) {
                System.out.println("\nStmt: " + stmt);
                deps.forEach(dep -> System.out.println("  " + dep));
            }
        });

        System.out.println("\nLoop Dependencies:");
        loopDependencies.forEach((stmt, deps) -> {
            if (!deps.isEmpty()) {
                System.out.println("\nStmt: " + stmt);
                deps.forEach(dep -> System.out.println("  " + dep));
            }
        });
    }
}