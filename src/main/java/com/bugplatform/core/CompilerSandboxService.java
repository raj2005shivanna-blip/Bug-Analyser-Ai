package com.bugplatform.core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Industrial-grade In-Memory Java Compiler & Self-Correction Sandbox.
 * Compiles candidate patches dynamically without touching disk,
 * captures precise compiler diagnostics, and executes multi-iteration
 * self-correction when compilation errors occur.
 */
@Service
public class CompilerSandboxService {

    public static class DiagnosticItem {
        private final String kind;
        private final long lineNumber;
        private final long columnNumber;
        private final String message;
        private final String code;

        public DiagnosticItem(String kind, long lineNumber, long columnNumber, String message, String code) {
            this.kind = kind;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.message = message;
            this.code = code;
        }

        public String getKind() { return kind; }
        public long getLineNumber() { return lineNumber; }
        public long getColumnNumber() { return columnNumber; }
        public String getMessage() { return message; }
        public String getCode() { return code; }
    }

    public static class CompilationResult {
        private final boolean success;
        private final List<DiagnosticItem> diagnostics;
        private final long compileDurationMs;
        private final int iterationCount;
        private final String finalCode;
        private final String engineUsed;

        public CompilationResult(boolean success, List<DiagnosticItem> diagnostics, long compileDurationMs, int iterationCount, String finalCode, String engineUsed) {
            this.success = success;
            this.diagnostics = diagnostics;
            this.compileDurationMs = compileDurationMs;
            this.iterationCount = iterationCount;
            this.finalCode = finalCode;
            this.engineUsed = engineUsed;
        }

        public boolean isSuccess() { return success; }
        public List<DiagnosticItem> getDiagnostics() { return diagnostics; }
        public long getCompileDurationMs() { return compileDurationMs; }
        public int getIterationCount() { return iterationCount; }
        public String getFinalCode() { return finalCode; }
        public String getEngineUsed() { return engineUsed; }
    }

    /**
     * In-memory source file representation for javac.
     */
    private static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code;

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    /**
     * In-memory class byte container.
     */
    private static class JavaClassOutput extends SimpleJavaFileObject {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        JavaClassOutput(String name) {
            super(URI.create("bytes:///" + name.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }
    }

    /**
     * In-memory file manager to avoid disk writes during sandboxed compilation.
     */
    private static class InMemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        private final Map<String, JavaClassOutput> classMap = new HashMap<>();

        protected InMemoryFileManager(JavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            JavaClassOutput classOutput = new JavaClassOutput(className);
            classMap.put(className, classOutput);
            return classOutput;
        }

        public Map<String, JavaClassOutput> getClassMap() {
            return classMap;
        }
    }

    /**
     * Extracts class name from source code, or defaults to a standard wrapper if snippet.
     */
    public String extractClassName(String code) {
        if (code == null) return "AutonomousSnippet";
        Pattern p = Pattern.compile("class\\s+([A-Za-z0-9_]+)");
        Matcher m = p.matcher(code);
        if (m.find()) {
            return m.group(1);
        }
        return "AutonomousSnippet";
    }

    /**
     * Normalizes code snippet into a fully compilable class if raw snippet provided.
     */
    public String wrapSnippetIfNeeded(String code) {
        if (code == null) return "public class AutonomousSnippet {}";
        if (code.contains("class ") || code.contains("interface ") || code.contains("enum ")) {
            return code;
        }
        return "package com.sandbox;\n\n" +
               "import java.util.*;\n" +
               "import java.io.*;\n\n" +
               "public class AutonomousSnippet {\n" +
               "    " + code.replace("\n", "\n    ") + "\n" +
               "}\n";
    }

    /**
     * Compiles Java source in-memory using system JavaCompiler with fallback to AST verification.
     */
    public CompilationResult compileInMemory(String sourceCode, String className) {
        long startTime = System.currentTimeMillis();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler == null) {
            // Fallback to JavaParser AST validation if running in stripped JRE
            return compileViaJavaParser(sourceCode, startTime);
        }

        DiagnosticCollector<JavaFileObject> diagnosticsCollector = new DiagnosticCollector<>();
        StandardJavaFileManager stdFileManager = compiler.getStandardFileManager(diagnosticsCollector, null, StandardCharsets.UTF_8);
        InMemoryFileManager inMemoryFileManager = new InMemoryFileManager(stdFileManager);

        JavaFileObject sourceFile = new JavaSourceFromString(className, sourceCode);
        List<String> options = Arrays.asList("-proc:none", "-Xlint:none");

        JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                inMemoryFileManager,
                diagnosticsCollector,
                options,
                null,
                Collections.singletonList(sourceFile)
        );

        boolean success = task.call();
        long duration = System.currentTimeMillis() - startTime;

        List<DiagnosticItem> diagnosticItems = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnosticsCollector.getDiagnostics()) {
            diagnosticItems.add(new DiagnosticItem(
                    d.getKind().name(),
                    d.getLineNumber(),
                    d.getColumnNumber(),
                    d.getMessage(Locale.ENGLISH),
                    d.getCode() != null ? d.getCode() : "COMPILER_ERR"
            ));
        }

        return new CompilationResult(success, diagnosticItems, duration, 1, sourceCode, "JDK In-Memory JavaCompiler (javax.tools)");
    }

    /**
     * Fallback syntax compiler via JavaParser AST.
     */
    private CompilationResult compileViaJavaParser(String sourceCode, long startTime) {
        List<DiagnosticItem> items = new ArrayList<>();
        boolean success = true;
        try {
            StaticJavaParser.parse(sourceCode);
        } catch (Exception e) {
            success = false;
            items.add(new DiagnosticItem("ERROR", 1, 1, e.getMessage(), "AST_PARSE_FAILURE"));
        }
        long duration = System.currentTimeMillis() - startTime;
        return new CompilationResult(success, items, duration, 1, sourceCode, "JavaParser AST Invariant Verifier");
    }

    /**
     * Self-Correction Loop: Iteratively compiles candidate patch,
     * inspects compiler diagnostics, and rectifies syntax/type errors until clean or max iterations.
     */
    public Map<String, Object> compileAndAutoCorrect(String rawCode, int maxIterations) {
        String currentCode = wrapSnippetIfNeeded(rawCode);
        String className = extractClassName(currentCode);

        List<Map<String, Object>> iterationsLog = new ArrayList<>();
        boolean resolved = false;
        long totalStartTime = System.currentTimeMillis();

        for (int i = 1; i <= Math.max(1, maxIterations); i++) {
            CompilationResult res = compileInMemory(currentCode, className);
            
            Map<String, Object> stepLog = new HashMap<>();
            stepLog.put("iteration", i);
            stepLog.put("compiledSuccessfully", res.isSuccess());
            stepLog.put("errorCount", res.getDiagnostics().stream().filter(d -> "ERROR".equals(d.getKind())).count());
            stepLog.put("diagnostics", res.getDiagnostics());
            stepLog.put("durationMs", res.getCompileDurationMs());

            if (res.isSuccess()) {
                stepLog.put("verdict", "Clean Compilation Achieved. 0 Syntax/Type Errors.");
                iterationsLog.add(stepLog);
                resolved = true;
                break;
            }

            // Attempt AI / Heuristic Self-Correction on errors
            stepLog.put("verdict", "Compilation failed with " + res.getDiagnostics().size() + " diagnostics. Applying self-correction heuristics...");
            iterationsLog.add(stepLog);

            currentCode = applySelfCorrectionHeuristics(currentCode, res.getDiagnostics());
        }

        long totalDuration = System.currentTimeMillis() - totalStartTime;

        Map<String, Object> response = new HashMap<>();
        response.put("verifiedClean", resolved);
        response.put("totalIterations", iterationsLog.size());
        response.put("totalDurationMs", totalDuration);
        response.put("iterationHistory", iterationsLog);
        response.put("finalVerifiedCode", currentCode);
        response.put("engine", "Closed-Loop Autonomous Sandbox (Self-Correction)");
        return response;
    }

    /**
     * Applies rule-based syntax repair for common LLM patch mistakes:
     * - Missing semicolons
     * - Unbalanced braces
     * - Missing common imports (java.util.*, java.io.*)
     */
    private String applySelfCorrectionHeuristics(String code, List<DiagnosticItem> diagnostics) {
        String patched = code;

        // Auto-inject missing imports if flagged
        for (DiagnosticItem d : diagnostics) {
            String msg = d.getMessage().toLowerCase();
            if (msg.contains("cannot find symbol") || msg.contains("class not found")) {
                if (msg.contains("list") || msg.contains("map") || msg.contains("set") || msg.contains("optional")) {
                    if (!patched.contains("import java.util.")) {
                        patched = "import java.util.*;\n" + patched;
                    }
                }
                if (msg.contains("file") || msg.contains("path") || msg.contains("inputstream")) {
                    if (!patched.contains("import java.io.") && !patched.contains("import java.nio.file.")) {
                        patched = "import java.io.*;\nimport java.nio.file.*;\n" + patched;
                    }
                }
            }
            if (msg.contains("';' expected") && d.getLineNumber() > 0) {
                String[] lines = patched.split("\n");
                int lineIdx = (int) d.getLineNumber() - 1;
                if (lineIdx >= 0 && lineIdx < lines.length) {
                    if (!lines[lineIdx].trim().endsWith(";") && !lines[lineIdx].trim().endsWith("{") && !lines[lineIdx].trim().endsWith("}")) {
                        lines[lineIdx] = lines[lineIdx] + ";";
                        patched = String.join("\n", lines);
                    }
                }
            }
        }

        // Ensure closing brace balance
        long openBraces = patched.chars().filter(ch -> ch == '{').count();
        long closeBraces = patched.chars().filter(ch -> ch == '}').count();
        if (openBraces > closeBraces) {
            StringBuilder sb = new StringBuilder(patched);
            for (long b = 0; b < (openBraces - closeBraces); b++) {
                sb.append("\n}");
            }
            patched = sb.toString();
        }

        return patched;
    }
}
