package org.example.programs;
public class WhileExample {
    public static int sumToN(int n) {
        int i = 0, s = 0;
        while (i <= n) { s += i; i++; }
        return s;
    }
}
