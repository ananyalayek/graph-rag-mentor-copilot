package com.magicbus.careercatalyst.onboarding;

import java.util.Map;

public class OnboardingResult {
    private boolean verified;
    private String studentId;
    private Map<String, DocumentExtractionResult> documents;
    private String notes;
    private String blobPath;
    private String blobUploadError;

    public OnboardingResult() {}

    public OnboardingResult(boolean verified, String studentId, Map<String, DocumentExtractionResult> documents, String notes, String blobPath, String blobUploadError) {
        this.verified = verified;
        this.studentId = studentId;
        this.documents = documents;
        this.notes = notes;
        this.blobPath = blobPath;
        this.blobUploadError = blobUploadError;
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

    public String getBlobPath() {
        return blobPath;
    }

    public void setBlobPath(String blobPath) {
        this.blobPath = blobPath;
    }

    public String getBlobUploadError() {
        return blobUploadError;
    }

    public void setBlobUploadError(String blobUploadError) {
        this.blobUploadError = blobUploadError;
    }
}
