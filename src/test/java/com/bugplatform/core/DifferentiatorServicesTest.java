package com.bugplatform.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class DifferentiatorServicesTest {

    @Autowired
    private CompilerSandboxService compilerSandboxService;

    @Autowired
    private SpectrumFaultLocalizationService sbflService;

    @Autowired
    private TaintAndCallGraphService taintService;

    @Autowired
    private PatchQualityAndAntiPatternService shieldService;

    @Autowired
    private BlastRadiusService blastRadiusService;

    @Autowired
    private MutationTestingService mutationService;

    @Autowired
    private ConcurrencyDeadlockService concurrencyDeadlockService;

    @Autowired
    private BinaryCompatibilityService binaryCompatibilityService;

    @Autowired
    private CodeChurnHotspotService churnHotspotService;

    @Test
    @DisplayName("Test 1: In-Memory Compiler Sandbox & Diagnostics")
    public void testCompilerSandbox() {
        String validCode = "public class HelloSandbox {\n" +
                           "    public String sayHello(String name) {\n" +
                           "        return \"Hello, \" + (name != null ? name : \"anonymous\");\n" +
                           "    }\n" +
                           "}\n";
        CompilerSandboxService.CompilationResult result = compilerSandboxService.compileInMemory(validCode, "HelloSandbox");
        assertNotNull(result);
        assertTrue(result.isSuccess(), "Clean Java code should compile with 0 diagnostics errors");

        // Test Self-Correction Loop on slightly malformed code
        Map<String, Object> autoCorrect = compilerSandboxService.compileAndAutoCorrect("public void runTask() { List<String> list = new ArrayList<>()", 3);
        assertNotNull(autoCorrect);
        assertTrue(autoCorrect.containsKey("iterationHistory"));
    }

    @Test
    @DisplayName("Test 2: Spectrum-Based Fault Localization (Ochiai & Red-Green Synthesizer)")
    public void testSBFL() {
        String buggyCode = "public class Demo {\n" +
                           "    public void process(String input) {\n" +
                           "        Object data = null;\n" +
                           "        System.out.println(data.toString());\n" +
                           "    }\n" +
                           "}\n";
        SpectrumFaultLocalizationService.SBFLReport report = sbflService.analyzeFaultSpectrum(buggyCode, "Demo.java");
        assertNotNull(report);
        assertNotNull(report.getMostSuspiciousStatement());
        assertTrue(report.getMostSuspiciousStatement().getOchiaiScore() > 0.0);
        assertNotNull(report.getReproducerUnitTest());
        assertTrue(report.getReproducerUnitTest().contains("assertDoesNotThrow"));
    }

    @Test
    @DisplayName("Test 3: Inter-Procedural Call Graph & Taint Tracking")
    public void testTaintTracking() {
        String controllerCode = "public class UserController {\n" +
                                "    public void handleRequest(String userInput) {\n" +
                                "        service.execute(userInput);\n" +
                                "    }\n" +
                                "}\n";
        TaintAndCallGraphService.TaintAnalysisResult taintResult = taintService.analyzeCodebaseTaint(controllerCode, "UserController.java");
        assertNotNull(taintResult);
        assertFalse(taintResult.getNodes().isEmpty());
        assertFalse(taintResult.getEdges().isEmpty());
        assertFalse(taintResult.getTaintPath().isEmpty());
        assertTrue(taintResult.getTotalTaintedSinks() > 0);
    }

    @Test
    @DisplayName("Test 4: AI Hallucination & Patch Anti-Pattern Shield")
    public void testHallucinationShield() {
        String orig = "public void doWork() { validate(); }";
        // Swallowed exception anti-pattern:
        String badPatch = "public void doWork() { try { validate(); } catch(Exception e) {} }";
        PatchQualityAndAntiPatternService.ShieldAuditReport audit = shieldService.auditPatchQuality(orig, badPatch);
        assertNotNull(audit);
        assertFalse(audit.getViolations().isEmpty(), "Should catch swallowed exception anti-pattern");
        assertEquals("SHIELD-01", audit.getViolations().get(0).getRuleId());

        // Good patch:
        String goodPatch = "public void doWork() { if (isValid()) { validate(); } }";
        PatchQualityAndAntiPatternService.ShieldAuditReport goodAudit = shieldService.auditPatchQuality(orig, goodPatch);
        assertTrue(goodAudit.isPassesSafetyGate(), "Clean defensive patch should pass shield audit");
    }

    @Test
    @DisplayName("Test 5: Blast Radius & Dependency Impact Engine")
    public void testBlastRadius() {
        String orig = "public void process() {}";
        String patch = "public void process() { safeCheck(); }";
        BlastRadiusService.BlastRadiusReport report = blastRadiusService.calculateBlastRadius("Core.java", orig, patch);
        assertNotNull(report);
        assertTrue(report.getBlastRadiusScore() > 0);
        assertFalse(report.getImpactedComponents().isEmpty());
        assertFalse(report.getImpactedEndpoints().isEmpty());
    }

    @Test
    @DisplayName("Test 6: AST Mutation Testing (PIT-Style Mutation Score - 0% AI)")
    public void testMutationTesting() {
        String code = "public class Calc {\n" +
                      "    public int calculate(int a, int b) {\n" +
                      "        if (a > 10) return a + b;\n" +
                      "        return 0;\n" +
                      "    }\n" +
                      "}\n";
        MutationTestingService.MutationReport report = mutationService.runMutationAnalysis(code, "Calc.java");
        assertNotNull(report);
        assertTrue(report.getTotalMutantsGenerated() > 0);
        assertTrue(report.getMutationScorePercentage() > 0.0);
        assertNotNull(report.getTestRobustnessVerdict());
    }

    @Test
    @DisplayName("Test 7: Concurrency Deadlock & Lock Acquisition Graph (0% AI)")
    public void testDeadlockDetection() {
        String concurrentCode = "public class SynchDemo {\n" +
                                "    private final Object lockA = new Object();\n" +
                                "    private final Object lockB = new Object();\n" +
                                "    public void task() {\n" +
                                "        synchronized(lockA) {\n" +
                                "            synchronized(lockB) { doWork(); }\n" +
                                "        }\n" +
                                "    }\n" +
                                "}\n";
        ConcurrencyDeadlockService.ConcurrencyReport report = concurrencyDeadlockService.analyzeConcurrencySafety(concurrentCode, "SynchDemo.java");
        assertNotNull(report);
        assertFalse(report.getLockAcquisitionGraph().isEmpty());
        assertNotNull(report.getOverallVerdict());
    }

    @Test
    @DisplayName("Test 8: JLS Binary & Source Backwards Compatibility (0% AI)")
    public void testBinaryCompatibility() {
        String orig = "public class ApiContract { public String fetchUser(int id) { return \"user\"; } }";
        // Method deleted = binary break:
        String brokenPatch = "public class ApiContract { private String fetchUser(int id) { return \"user\"; } }";
        BinaryCompatibilityService.CompatibilityReport report = binaryCompatibilityService.auditBinaryCompatibility(orig, brokenPatch);
        assertNotNull(report);
        assertFalse(report.isBinaryCompatible(), "Changing public method to private must trigger JLS binary break");
        assertTrue(report.getRecommendedSemVerBump().contains("MAJOR"));
    }

    @Test
    @DisplayName("Test 9: McCabe Cyclomatic Complexity & Commit Churn Hotspots (0% AI)")
    public void testChurnHotspots() {
        String complexCode = "public class Flow {\n" +
                             "    public void run(int x) {\n" +
                             "        if (x > 0) {\n" +
                             "            for (int i=0; i<x; i++) { if (i % 2 == 0) log(); }\n" +
                             "        }\n" +
                             "    }\n" +
                             "}\n";
        CodeChurnHotspotService.HotspotReport report = churnHotspotService.analyzeRepositoryHotspots(complexCode, "Flow.java");
        assertNotNull(report);
        assertTrue(report.getTotalModulesAnalyzed() > 0);
        assertTrue(report.getRepositoryAverageComplexity() > 1.0);
    }
}
