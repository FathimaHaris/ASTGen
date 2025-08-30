package org.example.programs.specified;

public class IntermediateReturn {
    public static void main(String[] args) {
        System.out.println(checkEven(4));
        System.out.println(checkEven(5));
    }

    public static boolean checkEven(int number) {
        if (number % 2 == 0) {
            return true;
        }
        return false;
    }
}
