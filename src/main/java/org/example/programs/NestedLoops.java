package org.example.programs;
public class NestedLoops {
    public static int pairs(int n) {
        int c = 0;
        for (int i = 0; i < n; i++) {
            int j = 0;
            while (j < n) { c += (i * j); j++; }
        }
        return c;
    }
}
