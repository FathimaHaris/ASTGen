package org.example.analyzer.dependency;

public class LoopDependency {
    private boolean isCarried;
    private String variable;
    private int distance; // For vectorization analysis

    public LoopDependency(boolean isCarried, String variable, int distance) {
        this.isCarried = isCarried;
        this.variable = variable;
        this.distance = distance;
    }

    // Getters
    public boolean isCarried() { return isCarried; }
    public String getVariable() { return variable; }
    public int getDistance() { return distance; }

    @Override
    public String toString() {
        return (isCarried ? "CARRIED" : "INDEPENDENT") +
                "{" + variable + ", distance=" + distance + "}";
    }
}