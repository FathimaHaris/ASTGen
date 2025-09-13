package org.example.programs.specified;

public class DominatorTest {
    public static void simpleIfElse() {
        int x = 10;
        int y = 20;
        int result;

        if (x > 5) {
            result = x + y;
        } else {
            result = x - y;
        }

        System.out.println(result);
    }

    public static void simpleLoop() {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += i;
        }
        System.out.println(sum);
    }

    public static void nestedControl() {
        int a = 5;
        int b = 10;

        if (a > 0) {
            for (int i = 0; i < b; i++) {
                if (i % 2 == 0) {
                    a += i;
                }
            }
        }

        System.out.println(a);
    }
}