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
}
