package org.example.programs;

import java.util.Scanner;

public class Subtraction {
    public static int subtract(int a, int b) {
        return a - b;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter first number: ");
        int a = sc.nextInt();
        System.out.print("Enter second number: ");
        int b = sc.nextInt();

        int result = subtract(a, b);
        System.out.println("Result: " + result);
    }
}
