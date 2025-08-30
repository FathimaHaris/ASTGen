package org.example.programs.specified;

public class MultipleTryCatchFinally {
    public static void main(String[] args) {
        try {
            String[] arr = new String[1];
            System.out.println(arr[1]);
        } catch (ArithmeticException e) {
            System.out.println("Caught an arithmetic exception.");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Caught an array index out of bounds exception.");
        } finally {
            System.out.println("Finally block executed.");
        }
    }
}
