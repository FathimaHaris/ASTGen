//package org.example.analyzer;
//
//import sootup.core.inputlocation.AnalysisInputLocation;
//import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
//import sootup.java.core.JavaSootClass;
//import sootup.java.core.JavaSootMethod;
//import sootup.java.core.views.JavaView;
//
//import de.upb.sootup.core.jimple.basic.Local;
//import de.upb.sootup.core.jimple.statements.*;
//import de.upb.sootup.core.jimple.values.*;
//import de.upb.sootup.core.jimple.expressions.*;
//import de.upb.sootup.core.types.IntType;
//import de.upb.sootup.core.jimple.common.Unit;
//import de.upb.sootup.core.jimple.Jimple;
//
//import java.io.IOException;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.*;
//import java.util.*;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
//public class IRWithAuxiliary {
//
//    public static void main(String[] args) throws IOException {
//        String classPath = (args.length > 0) ? args[0] : "target/classes";
//        String packagePrefix = (args.length > 1) ? args[1] : "org.example.programs";
//
//        List<AnalysisInputLocation> inputLocations = List.of(
//                new JavaClassPathAnalysisInputLocation(classPath));
//        JavaView view = new JavaView(inputLocations);
//
//        List<String> classNames = findClasses(classPath, packagePrefix);
//
//        if (classNames.isEmpty()) {
//            System.err.println("No classes found under package: " + packagePrefix);
//            return;
//        }
//
//        System.out.println("Discovered classes:");
//        classNames.forEach(c -> System.out.println("  • " + c));
//
//        Path outRoot = Paths.get("target/ir");
//        Files.createDirectories(outRoot);
//
//        for (String className : classNames) {
//            Optional<JavaSootClass> opt = view.getClass(view.getIdentifierFactory().getClassType(className));
//            if (opt.isEmpty()) {
//                System.err.println("Class not found in view: " + className);
//                continue;
//            }
//            JavaSootClass sc = opt.get();
//            System.out.println("\n================ CLASS: " + className + " ================");
//
//            for (JavaSootMethod m : sc.getMethods()) {
//                System.out.println("\n--- METHOD: " + m.getName() + " ---");
//                if (!m.hasBody()) {
//                    System.out.println("  <no body>");
//                    continue;
//                }
//                var body = m.getBody();
//
//                // ===== AUGMENTATION START =====
//                var units = body.getUnits();
//
//                // Add an auxiliary local once per method
//                Local aux = new Local("$aux0", IntType.getInstance());
//                body.getLocals().add(aux);
//
//                // Insert initialization at start: $aux0 = 0;
//                AssignStatement initAux = Jimple.newAssignStatement(
//                        aux,
//                        IntConstant.getInstance(0)
//                );
//                units.addFirst(initAux);
//
//                // Insert extra statement after every return
//                for (Unit u : List.copyOf(units)) {
//                    if (u instanceof ReturnStatement || u instanceof ReturnVoidStatement) {
//                        // $aux0 = $aux0 + 1;
//                        AddExpression plusOne = Jimple.newAddExpr(aux, IntConstant.getInstance(1));
//                        AssignStatement bump = Jimple.newAssignStatement(aux, plusOne);
//                        units.insertAfter(bump, u);
//                    }
//                }
//                // ===== AUGMENTATION END =====
//
//                // Print to console
//                System.out.println(body);
//
//
//            }
//        }
//    }
//
//    private static List<String> findClasses(String classesRoot, String packagePrefix) throws IOException {
//        Path root = Paths.get(classesRoot);
//        if (!Files.exists(root)) return List.of();
//
//        String pkgPath = packagePrefix.replace('.', '/');
//        Path start = root.resolve(pkgPath);
//        if (!Files.exists(start)) return List.of();
//
//        try (Stream<Path> stream = Files.walk(start)) {
//            return stream
//                    .filter(p -> p.toString().endsWith(".class"))
//                    .filter(p -> !p.getFileName().toString().contains("$")) // skip inner classes
//                    .map(root::relativize)
//                    .map(p -> p.toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
//                    .collect(Collectors.toList());
//        }
//    }
//}
