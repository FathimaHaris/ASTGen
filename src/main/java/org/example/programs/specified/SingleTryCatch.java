package org.example.programs.specified;

public class SingleTryCatch {
    public static void main(String[] args) {
        try {
            int result = 10 / 0;
            System.out.println(result);
        } catch (ArithmeticException e) {
            System.out.println("Caught an arithmetic exception.");
        }
    }
}
