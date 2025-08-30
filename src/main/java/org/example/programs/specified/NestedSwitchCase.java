package org.example.programs.specified;

public class NestedSwitchCase {
    public static void main(String[] args) {
        char branch = 'C';
        int year = 3;
        switch (year) {
            case 1:
                System.out.println("First year.");
                break;
            case 2:
                switch (branch) {
                    case 'C':
                        System.out.println("Second year CS");
                        break;
                    default:
                        System.out.println("Second year, other branch.");
                        break;
                }
                break;
            default:
                System.out.println("Other year.");
                break;
        }
    }
}
