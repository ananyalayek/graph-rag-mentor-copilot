package com.magicbus.careercatalyst.onboarding;

public class DocumentExtractionResult {
    private String name;
    private String dob;
    private String idNumber;
    private String annualIncome;
    private double confidence;

    public DocumentExtractionResult() {}

    public DocumentExtractionResult(String name, String dob, String idNumber, String annualIncome, double confidence) {
        this.name = name;
        this.dob = dob;
        this.idNumber = idNumber;
        this.annualIncome = annualIncome;
        this.confidence = confidence;
    }

    public String getName() {
        return name;
    }

    public String getDob() {
        return dob;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public String getAnnualIncome() {
        return annualIncome;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDob(String dob) {
        this.dob = dob;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public void setAnnualIncome(String annualIncome) {
        this.annualIncome = annualIncome;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
