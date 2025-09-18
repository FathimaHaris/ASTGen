package org.example.analyzer;



import org.example.analyzer.dependency.DefUseAnalyzer;
import org.example.analyzer.dependency.DependencyAnalyzer;
import org.example.analyzer.dependency.DependencyResult;
import org.example.analyzer.dependency.DominatorAnalyzer;
import org.example.analyzer.dependency.LoopAnalyzer;

import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.core.types.ClassType;
import sootup.core.model.Body;
import junit.framework.TestCase;

import java.util.List;
import java.util.Optional;

    public class DependencyTest extends TestCase {

        private JavaView view;

        @Override
        protected void setUp() throws Exception {
            super.setUp();
            // Initialize SootUp view (point to test classes)
            List<AnalysisInputLocation> inputLocations = List.of(
                    new JavaClassPathAnalysisInputLocation("target/test-classes"),
                    new JavaClassPathAnalysisInputLocation("target/classes"));
            view = new JavaView(inputLocations);
        }

        public void testSimpleLoopDependencies() {
            analyzeDependenciesForClass("org.example.programs.specified.SingleTryCatch");
        }

        public void testConditionalDependencies() {
            analyzeDependenciesForClass("org.example.programs.specified.ConditionalExample");
        }

        public void testArrayOperationsDependencies() {
            analyzeDependenciesForClass("org.example.programs.specified.ArrayOperations");
        }

        public void testBreakStatementDependencies() {
            analyzeDependenciesForClass("org.example.programs.specified.BreakStatement");
        }

        public void testContinueStatementDependencies() {
            analyzeDependenciesForClass("org.example.programs.specified.ContinueStatement");
        }

        public void testSwitchCaseDependencies() {
            analyzeDependenciesForClass("org.example.programs.specified.SwitchCase");
        }

        private void analyzeDependenciesForClass(String className) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("ANALYZING DEPENDENCIES FOR: " + className);
            System.out.println("=".repeat(60));

            ClassType classType = view.getIdentifierFactory().getClassType(className);
            Optional<JavaSootClass> opt = view.getClass(classType);

            if (opt.isEmpty()) {
                System.out.println("Class not found: " + className);
                return;
            }

            JavaSootClass sc = opt.get();

            for (JavaSootMethod m : sc.getMethods()) {
                System.out.println("\n--- METHOD: " + m.getName() + " ---");

                Body body = m.getBody();
                if (body == null) {
                    System.out.println("  <no body>");
                    continue;
                }

                StmtGraph<?> cfg = body.getStmtGraph();

                // Use your existing dependency analysis code
                DefUseAnalyzer defUseAnalyzer = new DefUseAnalyzer(cfg);
                defUseAnalyzer.printDefUseSets();

                DependencyAnalyzer depAnalyzer = new DependencyAnalyzer(cfg, defUseAnalyzer);
                DependencyResult result = depAnalyzer.analyze();

                result.printResults();
                depAnalyzer.printReachingDefinitions();

                assertNotNull("Dependency analysis should return a result", result);
            }
        }


        public void testDominatorAnalysis() {
            analyzeDominatorsForClass("org.example.programs.specified.DominatorTest");
        }

        private void analyzeDominatorsForClass(String className) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("ANALYZING DOMINATORS FOR: " + className);
            System.out.println("=".repeat(60));

            ClassType classType = view.getIdentifierFactory().getClassType(className);
            Optional<JavaSootClass> opt = view.getClass(classType);

            if (opt.isEmpty()) {
                System.out.println("Class not found: " + className);
                return;
            }

            JavaSootClass sc = opt.get();

            for (JavaSootMethod m : sc.getMethods()) {
                System.out.println("\n--- METHOD: " + m.getName() + " ---");

                Body body = m.getBody();
                if (body == null) {
                    System.out.println("  <no body>");
                    continue;
                }

                StmtGraph<?> cfg = body.getStmtGraph();

                // Test dominator analysis
                DominatorAnalyzer domAnalyzer = new DominatorAnalyzer(cfg);
                domAnalyzer.printDominators();
                domAnalyzer.printDominatorTree();
            }
        }



        public void testLoopAnalysis() {
            analyzeLoopsForClass("org.example.programs.specified.LoopTest");
        }

        private void analyzeLoopsForClass(String className) {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("ANALYZING LOOPS FOR: " + className);
            System.out.println("=".repeat(60));

            ClassType classType = view.getIdentifierFactory().getClassType(className);
            Optional<JavaSootClass> opt = view.getClass(classType);

            if (opt.isEmpty()) {
                System.out.println("Class not found: " + className);
                return;
            }

            JavaSootClass sc = opt.get();

            for (JavaSootMethod m : sc.getMethods()) {
                System.out.println("\n--- METHOD: " + m.getName() + " ---");

                Body body = m.getBody();
                if (body == null) {
                    System.out.println("  <no body>");
                    continue;
                }

                StmtGraph<?> cfg = body.getStmtGraph();

                // Test loop analysis
                DefUseAnalyzer defUseAnalyzer = new DefUseAnalyzer(cfg);
                Map<Stmt, Set<Stmt>> reachingDefs = defUseAnalyzer.computeReachingDefinitions();
                DominatorAnalyzer domAnalyzer = new DominatorAnalyzer(cfg);
                LoopAnalyzer loopAnalyzer = new LoopAnalyzer(cfg, domAnalyzer);
                loopAnalyzer.printLoopAnalysis();
            }
        }
    }

