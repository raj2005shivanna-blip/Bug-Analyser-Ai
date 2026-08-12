package com.bugplatform.core;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Controller
public class HealthController {

    private final BugRepository bugRepository;
    private final GitHubService gitHubService;
    private final OpenAIService openAIService;

    public HealthController(BugRepository bugRepository, GitHubService gitHubService, OpenAIService openAIService) {
        this.bugRepository = bugRepository;
        this.gitHubService = gitHubService;
        this.openAIService = openAIService;
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
        return bugRepository.save(savedBug);
    }

    @ResponseBody
    @PostMapping("/api/bugs/{id}/fix")
    public BugReport autoFixAndOpenPR(@PathVariable String id) {
        BugReport bug = bugRepository.findById(id).orElseThrow(() -> new RuntimeException("Bug not found"));
        Map<String, String> prDetails = gitHubService.pushFixAndOpenPR("https://github.com/user/scanned-project", bug.getTitle(), bug.getPatchedCode());
        
        bug.setStatus("RESOLVED & PR MERGED");
        bug.setGithubPrLink(prDetails.get("pullRequestUrl"));
        return bugRepository.save(bug);
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