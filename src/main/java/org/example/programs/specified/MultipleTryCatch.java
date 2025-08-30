package org.example.programs.specified;

public class MultipleTryCatch {
    public static void main(String[] args) {
        try {
            String[] arr = new String[1];
            System.out.println(arr[1]); // Will throw ArrayIndexOutOfBoundsException
            int result = 10 / 0;      // This line is unreachable
        } catch (ArithmeticException e) {
            System.out.println("Caught an arithmetic exception.");
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Caught an array index out of bounds exception.");
        }
    }
}
