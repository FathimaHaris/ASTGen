package org.example.programs;

public class SimpleAdd {
    public static int add(int a, int b) {
        int sum = a + b;
        return sum;
    }

    public static void main(String[] args) {
        System.out.println("Result: " + add(3, 4));
    }
}
