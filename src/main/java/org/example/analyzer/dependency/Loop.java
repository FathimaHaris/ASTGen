package org.example.analyzer.dependency;

import sootup.core.jimple.common.stmt.Stmt;
import java.util.*;

public class Loop {
    private Stmt header;
    private Set<Stmt> statements;
    private Set<Loop> nestedLoops;
    private Loop parentLoop;

    public Loop(Stmt header) {
        this.header = header;
        this.statements = new HashSet<>();
        this.nestedLoops = new HashSet<>();
        this.statements.add(header);
    }

    public void addStatement(Stmt stmt) {
        statements.add(stmt);
    }

    public boolean contains(Stmt stmt) {
        return statements.contains(stmt);
    }

    public Stmt getHeader() {
        return header;
    }

    public Set<Stmt> getStatements() {
        return Collections.unmodifiableSet(statements);
    }

    public void addNestedLoop(Loop loop) {
        nestedLoops.add(loop);
        loop.setParentLoop(this);
    }

    public Set<Loop> getNestedLoops() {
        return Collections.unmodifiableSet(nestedLoops);
    }

    public void setParentLoop(Loop parent) {
        this.parentLoop = parent;
    }

    public Loop getParentLoop() {
        return parentLoop;
    }

    public int getNestingDepth() {
        int depth = 0;
        Loop current = this;
        while (current.parentLoop != null) {
            depth++;
            current = current.parentLoop;
        }
        return depth;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Loop other = (Loop) obj;
        return header.equals(other.header);
    }

    @Override
    public int hashCode() {
        return header.hashCode();
    }

    @Override
    public String toString() {
        return "Loop{header=" + header + ", size=" + statements.size() + ", depth=" + getNestingDepth() + "}";
    }
}