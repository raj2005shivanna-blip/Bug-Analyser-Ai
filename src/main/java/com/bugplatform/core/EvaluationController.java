package com.bugplatform.core;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/api/evaluate")
    public Map<String, Object> evaluateModel(@RequestParam(required = false) String version) {
        return evaluationService.evaluateModel(version);
    }
}
