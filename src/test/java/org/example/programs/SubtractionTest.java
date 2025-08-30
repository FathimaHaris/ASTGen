package org.example.programs;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class SubtractionTest {

    @Test
    public void testPositiveNumbers() {
        assertEquals(5, Subtraction.subtract(10, 5));
    }

    @Test
    public void testNegativeResult() {
        assertEquals(-3, Subtraction.subtract(2, 5));
    }

    @Test
    public void testWithZero() {
        assertEquals(7, Subtraction.subtract(7, 0));
    }

    @Test
    public void testBothZero() {
        assertEquals(0, Subtraction.subtract(0, 0));
    }
}
