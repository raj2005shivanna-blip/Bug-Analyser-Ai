package com.bugplatform.core;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import java.util.UUID;

@Entity
public class BugFeedback {

    @Id
    private String id;
    private String bugId;
    
    @Column(columnDefinition = "TEXT")
    private String developerComment;
    
    private boolean predictionCorrect;

    public BugFeedback() {}

    public BugFeedback(String bugId, String developerComment, boolean predictionCorrect) {
        this.id = UUID.randomUUID().toString();
        this.bugId = bugId;
        this.developerComment = developerComment;
        this.predictionCorrect = predictionCorrect;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getBugId() { return bugId; }
    public void setBugId(String bugId) { this.bugId = bugId; }
    
    public String getDeveloperComment() { return developerComment; }
    public void setDeveloperComment(String developerComment) { this.developerComment = developerComment; }
    
    public boolean isPredictionCorrect() { return predictionCorrect; }
    public void setPredictionCorrect(boolean predictionCorrect) { this.predictionCorrect = predictionCorrect; }
}
