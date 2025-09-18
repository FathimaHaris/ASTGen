    package org.example.analyzer;

    import sootup.core.inputlocation.AnalysisInputLocation;
    import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
    import sootup.java.core.JavaSootClass;
    import sootup.java.core.JavaSootMethod;
    import sootup.java.core.views.JavaView;

    import java.io.IOException;
    import java.nio.charset.StandardCharsets;
    import java.nio.file.*;
    import java.util.List;
    import java.util.Optional;
    import java.util.stream.Collectors;
    import java.util.stream.Stream;

    public class IRGenerator {

        public static void main(String[] args) throws IOException {
            String classPath = (args.length > 0) ? args[0] : "target/classes";
            String packagePrefix = (args.length > 1) ? args[1] : "org.example.programs";

            List<AnalysisInputLocation> inputLocations = List.of(
                    new JavaClassPathAnalysisInputLocation(classPath));
            JavaView view = new JavaView(inputLocations);

            List<String> classNames = findClasses(classPath, packagePrefix);

            if (classNames.isEmpty()) {
                System.err.println("No classes found under package: " + packagePrefix);
                return;
            }

            System.out.println("Discovered classes:");
            classNames.forEach(c -> System.out.println("  • " + c));

            Path outRoot = Paths.get("target/ir");
            Files.createDirectories(outRoot);

            for (String className : classNames) {
                Optional<JavaSootClass> opt = view.getClass(view.getIdentifierFactory().getClassType(className));
                if (opt.isEmpty()) {
                    System.err.println("Class not found in view: " + className);
                    continue;
                }
                JavaSootClass sc = opt.get();
                System.out.println("\n================ CLASS: " + className + " ================");

                for (JavaSootMethod m : sc.getMethods()) {
                    System.out.println("\n--- METHOD: " + m.getName() + " ---");
                    if (!m.hasBody()) {
                        System.out.println("  <no body>");
                        continue;
                    }
                    var body = m.getBody();
                    // Print to console
                    System.out.println(body);

                    // Write to file: target/ir/<className>/<methodName>.jimple
                    String safeClass = className.replace('.', '_');
                    String safeMethod = m.getName().replaceAll("[^a-zA-Z0-9_]", "_");
                    Path classDir = outRoot.resolve(safeClass);
                    Files.createDirectories(classDir);
                    Path outFile = classDir.resolve(safeMethod + ".jimple");
                    try {
                        Files.writeString(outFile, body.toString(), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        System.out.println("  -> Wrote IR to: " + outFile.toAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("  ! Failed to write IR: " + e.getMessage());
                    }
                }
            }
        }

        private static List<String> findClasses(String classesRoot, String packagePrefix) throws IOException {
            Path root = Paths.get(classesRoot);
            if (!Files.exists(root)) return List.of();

            String pkgPath = packagePrefix.replace('.', '/');
            Path start = root.resolve(pkgPath);
            if (!Files.exists(start)) return List.of();

            try (Stream<Path> stream = Files.walk(start)) {
                return stream
                        .filter(p -> p.toString().endsWith(".class"))
                        .filter(p -> !p.getFileName().toString().contains("$")) // skip inner classes
                        .map(root::relativize)
                        .map(p -> p.toString().replace('/', '.').replace('\\', '.').replaceAll("\\.class$", ""))
                        .collect(Collectors.toList());
            }
        }
    }
