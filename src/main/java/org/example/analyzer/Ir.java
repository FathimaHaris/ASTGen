package org.example.analyzer;
import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.views.JavaView;

import java.util.List;
import java.util.Optional;

public class Ir {


        public static void generateIRForClass(String className) {
            String classPath = "target/classes";
            List<AnalysisInputLocation> inputLocations = List.of(
                    new JavaClassPathAnalysisInputLocation(classPath));
            JavaView view = new JavaView(inputLocations);

            Optional<JavaSootClass> opt = view.getClass(view.getIdentifierFactory().getClassType(className));
            if (opt.isEmpty()) {
                System.err.println("Class not found: " + className);
                return;
            }

            JavaSootClass sc = opt.get();
            for (JavaSootMethod m : sc.getMethods()) {
                if (!m.hasBody()) continue;
                System.out.println("\n--- METHOD: " + m.getName() + " ---");
                System.out.println(m.getBody()); // this prints Jimple-like IR
            }
        }


}
