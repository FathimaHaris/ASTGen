package org.example.programs.specified;

public class LoopTest {

    // Simple loop
    public static void simpleLoop() {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            sum += i;
        }
        System.out.println(sum);
    }

    // Nested loops
    public static void nestedLoops() {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 3; j++) {
                result += i * j;
            }
        }
        System.out.println(result);
    }

    // While loop
    public static void whileLoop() {
        int count = 0;
        int sum = 0;
        while (count < 10) {
            sum += count;
            count++;
        }
        System.out.println(sum);
    }

    // Do-while loop
    public static void doWhileLoop() {
        int count = 0;
        int sum = 0;
        do {
            sum += count;
            count++;
        } while (count < 10);
        System.out.println(sum);
    }

    // Loop with break/continue
    public static void loopWithControlFlow() {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            if (i == 5) {
                break;
            }
            if (i % 2 == 0) {
                continue;
            }
            sum += i;
        }
        System.out.println(sum);
    }
}