package org.example.programs.specified;

public class SingleTryCatchFinally {
    public static void main(String[] args) {
        try {
            int result = 10 / 0;
            System.out.println(result);
        } catch (ArithmeticException e) {
            System.out.println("Caught an arithmetic exception.");
        } finally {
            System.out.println("This code always runs.");
        }
    }
}
