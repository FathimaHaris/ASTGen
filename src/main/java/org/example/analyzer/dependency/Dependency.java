package org.example.analyzer.dependency;

import sootup.core.jimple.common.stmt.Stmt;
import java.util.Objects;

public class Dependency {
    public enum Type {
        RAW,    // Read After Write (true dependency)
        WAR,    // Write After Read (anti-dependency)
        WAW,    // Write After Write (output dependency)
        CONTROL,
        DEF_ORDER// Control dependency
    }

    private Type type;
    private Stmt source;
    private Stmt target;
    private String variable; // For data dependencies

    public Dependency(Type type, Stmt source, Stmt target, String variable) {
        this.type = type;
        this.source = source;
        this.target = target;
        this.variable = variable;
    }

    // Getters
    public Type getType() { return type; }
    public Stmt getSource() { return source; }
    public Stmt getTarget() { return target; }
    public String getVariable() { return variable; }

    @Override
    public String toString() {
        return type + "{" + (variable != null ? variable : "control") + "}: " +
                source + " → " + target;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Dependency that = (Dependency) obj;
        return type == that.type &&
                Objects.equals(source, that.source) &&
                Objects.equals(target, that.target) &&
                Objects.equals(variable, that.variable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, source, target, variable);
    }
}