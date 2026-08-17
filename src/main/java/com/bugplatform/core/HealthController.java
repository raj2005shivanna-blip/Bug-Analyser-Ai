package com.bugplatform.core;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import java.util.*;

@Controller
public class HealthController {

    private final BugRepository bugRepository;
    private final GitHubService gitHubService;
    private final OpenAIService openAIService;
    private final GitAutomationService gitAutomationService;

    public HealthController(BugRepository bugRepository, GitHubService gitHubService, OpenAIService openAIService, GitAutomationService gitAutomationService) {
        this.bugRepository = bugRepository;
        this.gitHubService = gitHubService;
        this.openAIService = openAIService;
        this.gitAutomationService = gitAutomationService;
    }

    @GetMapping("/")
    public String home() {
        return "index.html";
    }

    @ResponseBody
    @GetMapping("/api/bugs")
    public List<BugReport> getAllBugs() {
        return bugRepository.findAll();
    }

    @ResponseBody
    @PostMapping("/api/bugs")
    public BugReport createBug(@RequestBody BugReport newBug) {
        BugReport savedBug = new BugReport(
            newBug.getTitle(),
            newBug.getDescription(),
            newBug.getSeverity()
        );
        Map<String, Object> aiMetrics = openAIService.analyzeCodeWithRealAI(savedBug.getOriginalCode());
        savedBug.setComplexityScore((int) aiMetrics.get("cyclomaticComplexity"));
        savedBug.setMaintainability((String) aiMetrics.get("maintainabilityIndex"));
        
        Map<String, String> classification = openAIService.classifyBugDomain(savedBug.getTitle(), savedBug.getDescription());
        savedBug.setTechnicalDomain(classification.get("primaryDomain"));
        savedBug.setComponentName(classification.get("component"));
        savedBug.setSecondaryDomain(classification.get("secondaryDomain"));
        savedBug.setDomainConfidence(classification.get("confidence"));
        savedBug.setDomainReasoning(classification.get("reasoning"));
        
        return bugRepository.save(savedBug);
    }

    @ResponseBody
    @PostMapping("/api/bugs/{id}/fix")
    public BugReport autoFixAndOpenPR(@PathVariable String id) {
        BugReport bug = bugRepository.findById(id).orElseThrow(() -> new RuntimeException("Bug not found"));
        Map<String, String> prDetails = gitHubService.pushFixAndOpenPR("https://github.com/user/scanned-project", bug.getTitle(), bug.getPatchedCode());
        
        bug.setStatus("RESOLVED & PR MERGED");
        bug.setGithubPrLink(prDetails.get("pullRequestUrl"));
        bug.setResolvedAt(java.time.LocalDateTime.now().toString());
        return bugRepository.save(bug);
    }

    @ResponseBody
    @PostMapping("/api/repo/autofix")
    public Object triggerRepoAutofix(@RequestBody Map<String, String> payload) {
        String repoUrl = payload.get("repoUrl");
        if (repoUrl == null || !repoUrl.matches("^https://github\\.com/[\\w-]+/[\\w.-]+(?:\\.git)?$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid GitHub repository URL"));
        }
        return gitAutomationService.cloneAndFix(repoUrl).join();
    }

    @PostMapping("/api/zip/autofix")
    public ResponseEntity<?> triggerZipAutofix(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !Objects.requireNonNull(file.getOriginalFilename()).endsWith(".zip")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please upload a valid .zip file"));
        }
        try {
            byte[] fixedZip = gitAutomationService.processAndFixZip(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fixed_" + file.getOriginalFilename() + "\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(fixedZip);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @ResponseBody
    @GetMapping("/api/report/summary")
    public Map<String, Object> getAuditReport() {
        List<BugReport> allBugs = bugRepository.findAll();
        long totalFixed = allBugs.stream().filter(b -> b.getStatus().contains("RESOLVED")).count();
        int totalTokens = allBugs.stream().mapToInt(BugReport::getTokensUsed).sum();
        
        Map<String, Object> report = new HashMap<>();
        report.put("totalBugsDetected", allBugs.size());
        report.put("totalBugsFixed", totalFixed);
        report.put("successRate", allBugs.isEmpty() ? "0%" : Math.round(((double)totalFixed / allBugs.size()) * 100) + "%");
        report.put("totalTokensConsumed", totalTokens);
        report.put("estimatedHoursSaved", (totalFixed * 2.5) + " Hours");
        report.put("auditTimestamp", new Date().toString());
        report.put("fixedDefectsList", allBugs.stream().filter(b -> b.getStatus().contains("RESOLVED")).map(BugReport::getTitle).toList());
        return report;
    }
}