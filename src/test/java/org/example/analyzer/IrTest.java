package org.example.analyzer;

import junit.framework.TestCase;
public class IrTest extends TestCase {



        public void testSingleTryCatch() {
            Ir.generateIRForClass("org.example.programs.specified.SingleTryCatch");
        }

        public void testBreakStatement() {
            Ir.generateIRForClass("org.example.programs.specified.BreakStatement");
        }

    public void testContinueStatement() {
        Ir.generateIRForClass("org.example.programs.specified.ContinueStatement");
    }

    public void testIntermediateReturn() {
        Ir.generateIRForClass("org.example.programs.specified.IntermediateReturn");
    }

    public void testMultipleTryCatch() {
        Ir.generateIRForClass("org.example.programs.specified.MultipleTryCatch");
    }

    public void testMultipleTryCatchFinally() {
        Ir.generateIRForClass("org.example.programs.specified.MultipleTryCatchFinally");
    }

    public void testNestedSwitchCase() {
        Ir.generateIRForClass("org.example.programs.specified.NestedSwitchCase");
    }

    public void testSingleTryCatchFinally() {
        Ir.generateIRForClass("org.example.programs.specified.SingleTryCatchFinally");
    }

    public void testSwitchCase() {
        Ir.generateIRForClass("org.example.programs.specified.SwitchCase");
    }

    public void testThrowException() {
        Ir.generateIRForClass("org.example.programs.specified.ThrowException");
    }





}