package org.example.analyzer.dependency;

import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.basic.Value;

public class LoopDependency {
    public enum Type {
        CARRIED,       // Crosses iterations
        INDEPENDENT,   // Within same iteration
        UNKNOWN        // Cannot determine
    }

    private Type type;
    private Value variable;
    private int distance;
    private Stmt sourceStmt;
    private Stmt targetStmt;
    private Loop loop;

    public LoopDependency(Type type, Value variable, int distance,
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
    public Value getVariable() { return variable; }
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