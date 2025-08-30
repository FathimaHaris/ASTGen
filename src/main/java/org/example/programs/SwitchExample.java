package org.example.programs;
public class SwitchExample {
    public static int daysInMonth(int m) {
        int d;
        switch (m) {
            case 1: case 3: case 5: case 7: case 8: case 10: case 12: d = 31; break;
            case 4: case 6: case 9: case 11: d = 30; break;
            case 2: d = 28; break;
            default: d = -1;
        }
        return d;
    }
}
