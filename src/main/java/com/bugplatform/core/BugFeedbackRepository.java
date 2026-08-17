package com.bugplatform.core;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BugFeedbackRepository extends JpaRepository<BugFeedback, String> {
}
