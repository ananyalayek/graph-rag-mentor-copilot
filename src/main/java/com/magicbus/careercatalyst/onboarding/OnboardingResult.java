package com.magicbus.careercatalyst.onboarding;

import java.util.Map;

public class OnboardingResult {
    private boolean verified;
    private String studentId;
    private Map<String, DocumentExtractionResult> documents;
    private String notes;

    public OnboardingResult() {}

    public OnboardingResult(boolean verified, String studentId, Map<String, DocumentExtractionResult> documents, String notes) {
        this.verified = verified;
        this.studentId = studentId;
        this.documents = documents;
        this.notes = notes;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public Map<String, DocumentExtractionResult> getDocuments() {
        return documents;
    }

    public void setDocuments(Map<String, DocumentExtractionResult> documents) {
        this.documents = documents;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
