package org.example.programs.specified;

public class BreakStatement {
    public static void main(String[] args) {
        for (int i = 0; i < 5; i++) {
            if (i == 3) {
                System.out.println("Breaking at 3");
                break;
            }
            System.out.println("Count: " + i);
        }
    }
}
