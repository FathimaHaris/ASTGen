package org.example.programs.specified;

public class ContinueStatement {
    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            if (i == 2) {
                System.out.println("Skipping 2");
                continue;
            }
            System.out.println("Count: " + i);
        }
    }
}
