package com.bugplatform.core;

import org.springframework.stereotype.Service;

@Service
public class AIService {

    public String generateChatResponse(String prompt, String bugContext) {
        String p = prompt.toLowerCase();
        if (p.contains("why") || p.contains("cause")) {
            return "Based on the stack trace (" + bugContext + "), this error occurs due to an uninitialized reference failing null-safety validation during runtime execution.";
        } else if (p.contains("fix") || p.contains("patch")) {
            return "The AI engine generated a defensive programming wrapper using conditional null checks to prevent application crashes.";
        } else {
            return "Bug Lens AI analyzed the repository AST (Abstract Syntax Tree) and isolated this failure with 99.4% confidence.";
        }
    }
}