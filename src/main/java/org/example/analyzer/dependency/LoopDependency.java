package org.example.analyzer.dependency;

import sootup.core.jimple.common.stmt.Stmt;

public class LoopDependency {
    public enum Type {
        CARRIED,       // Crosses iterations
        INDEPENDENT,   // Within same iteration
        UNKNOWN        // Cannot determine
    }

    private Type type;
    private String variable;
    private int distance;      // For carried dependencies
    private Stmt sourceStmt;   // Definition statement
    private Stmt targetStmt;   // Use statement
    private Loop loop;         // Which loop this belongs to

    public LoopDependency(Type type, String variable, int distance,
                          Stmt sourceStmt, Stmt targetStmt, Loop loop) {
        this.type = type;
        this.variable = variable;
        this.distance = distance;
        this.sourceStmt = sourceStmt;
        this.targetStmt = targetStmt;
        this.loop = loop;
    }

    // Getters
    public Type getType() { return type; }
    public String getVariable() { return variable; }
    public int getDistance() { return distance; }
    public Stmt getSourceStmt() { return sourceStmt; }
    public Stmt getTargetStmt() { return targetStmt; }
    public Loop getLoop() { return loop; }

    public boolean isCarried() { return type == Type.CARRIED; }
    public boolean isIndependent() { return type == Type.INDEPENDENT; }

    @Override
    public String toString() {
        String typeStr = type == Type.CARRIED ? "CARRIED" :
                type == Type.INDEPENDENT ? "INDEPENDENT" : "UNKNOWN";

        return typeStr + "{" + variable +
                (type == Type.CARRIED ? ", distance=" + distance : "") +
                "} from " + sourceStmt + " to " + targetStmt;
    }
}