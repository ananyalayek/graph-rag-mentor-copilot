package com.magicbus.careercatalyst.onboarding;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import com.magicbus.careercatalyst.storage.BlobStorageService;

import java.util.HashMap;
import java.util.Map;

@Service
public class OnboardingService {

    private static final double MIN_CONFIDENCE = 0.80;

    private final AzureDocumentIntelligenceClient docClient;
    private final PowerAutomateNotifier notifier;
    private final StudentProfileLookup studentProfileLookup;
    private final BlobStorageService blobStorageService;
    private final boolean demoMode;
    private final boolean skipRules;

    public OnboardingService(AzureDocumentIntelligenceClient docClient,
                             PowerAutomateNotifier notifier,
                             StudentProfileLookup studentProfileLookup,
                             BlobStorageService blobStorageService,
                             @Value("${onboarding.demo-mode:false}") boolean demoMode,
                             @Value("${onboarding.demo-skip-rules:false}") boolean skipRules) {
        this.docClient = docClient;
        this.notifier = notifier;
        this.studentProfileLookup = studentProfileLookup;
        this.blobStorageService = blobStorageService;
        this.demoMode = demoMode;
        this.skipRules = skipRules;
    }

    public OnboardingResult verify(String studentId,
                                   MultipartFile aadhaar,
                                   MultipartFile pan,
                                   MultipartFile income,
                                   CandidateInfo candidateInfo) {
        try {
            DocumentExtractionResult aadhaarResult = docClient.analyzeIdDocument(aadhaar.getBytes(), "AADHAAR");
            boolean panProvided = pan != null;
            DocumentExtractionResult panResult = panProvided ? docClient.analyzeIdDocument(pan.getBytes(), "PAN")
                    : new DocumentExtractionResult("", "", "", "", 0.0);
            DocumentExtractionResult incomeResult = docClient.analyzeIncomeDocument(income.getBytes());

            String profileName = fetchStudentName(studentId, candidateInfo);
            if (demoMode && isEmptyResult(aadhaarResult) && isEmptyResult(panResult) && isEmptyResult(incomeResult)) {
                aadhaarResult = mockResult(profileName, "AADHAAR");
                panResult = mockResult(profileName, "PAN");
                incomeResult = mockResult(profileName, "INCOME");
            }
            boolean nameMatch = NameMatcher.allMatch(profileName,
                    aadhaarResult.getName(), panResult.getName(), incomeResult.getName());

            Map<String, DocumentExtractionResult> docs = new HashMap<>();
            docs.put("aadhaar", aadhaarResult);
            docs.put("pan", panResult);
            docs.put("income", incomeResult);

            checkConfidence(studentId, "aadhaar", aadhaarResult.getConfidence());
            if (pan != null) {
                checkConfidence(studentId, "pan", panResult.getConfidence());
            }
            checkConfidence(studentId, "income", incomeResult.getConfidence());

            VerificationDecision decision = decideEligibility(candidateInfo, aadhaarResult, incomeResult,
                    panProvided ? panResult : null, nameMatch);
            boolean verified = decision.isVerified();
            String notes = decision.reason();
            UploadResult uploadResult = uploadSubmission(studentId, candidateInfo, aadhaarResult, panResult, incomeResult, nameMatch, notes, verified);
            OnboardingResult result = new OnboardingResult(verified, studentId, docs, notes, uploadResult.blobPath(), uploadResult.error());
            return result;
        } catch (Exception e) {
            return new OnboardingResult(false, studentId, Map.of(), "Verification failed: " + e.getMessage(), "", e.getMessage());
        }
    }

    private void checkConfidence(String studentId, String docType, double confidence) {
        if (confidence < MIN_CONFIDENCE) {
            notifier.notifyLowConfidence(studentId, docType, confidence);
        }
    }

    private String fetchStudentName(String studentId, CandidateInfo candidateInfo) {
        if (studentId != null && !studentId.isBlank()) {
            return studentProfileLookup.getNameById(studentId);
        }
        if (candidateInfo != null && candidateInfo.name() != null) {
            return candidateInfo.name();
        }
        return "";
    }

    private boolean isEmptyResult(DocumentExtractionResult result) {
        return result == null
                || (result.getName() == null || result.getName().isBlank())
                && (result.getIdNumber() == null || result.getIdNumber().isBlank())
                && result.getConfidence() == 0.0;
    }

    private DocumentExtractionResult mockResult(String name, String idType) {
        String safeName = name == null ? "" : name;
        String idNumber = idType + "-DEMO-0001";
        return new DocumentExtractionResult(safeName, "", idNumber, "", 0.98);
    }

    private VerificationDecision decideEligibility(CandidateInfo candidateInfo,
                                                   DocumentExtractionResult aadhaar,
                                                   DocumentExtractionResult income,
                                                   DocumentExtractionResult pan,
                                                   boolean nameMatch) {
        if (!nameMatch && !skipRules) {
            return VerificationDecision.needsReview("Needs-Review: name mismatch");
        }

        if (!skipRules && (aadhaar == null || aadhaar.getConfidence() < MIN_CONFIDENCE)) {
            return VerificationDecision.needsReview("Needs-Review: Aadhaar confidence below threshold");
        }
        if (!skipRules && (income == null || income.getConfidence() < MIN_CONFIDENCE)) {
            return VerificationDecision.needsReview("Needs-Review: income confidence below threshold");
        }
        if (!skipRules && pan != null && pan.getConfidence() < MIN_CONFIDENCE) {
            return VerificationDecision.needsReview("Needs-Review: PAN confidence below threshold");
        }

        Integer ageFromDob = parseAgeFromDob(aadhaar.getDob());
        if (!skipRules && ageFromDob == null) {
            return VerificationDecision.needsReview("Needs-Review: age not detected");
        }
        if (!skipRules && (ageFromDob < 14 || ageFromDob > 25)) {
            return VerificationDecision.needsReview("Needs-Review: age outside 14-25");
        }

        if (candidateInfo != null && candidateInfo.ageRange() != null && !candidateInfo.ageRange().isBlank()) {
            AgeRange range = AgeRange.fromLabel(candidateInfo.ageRange());
            if (!skipRules && range != null && !range.contains(ageFromDob)) {
                return VerificationDecision.needsReview("Needs-Review: age range mismatch");
            }
        }

        Long incomeValue = parseIncome(income.getAnnualIncome());
        if (!skipRules && incomeValue == null) {
            return VerificationDecision.needsReview("Needs-Review: income not detected");
        }
        if (!skipRules && incomeValue > 500000L) {
            return VerificationDecision.needsReview("Needs-Review: income above 5 lakhs");
        }

        return VerificationDecision.verified();
    }

    private Integer parseAgeFromDob(String dob) {
        if (dob == null || dob.isBlank()) {
            return null;
        }
        try {
            java.time.LocalDate date = java.time.LocalDate.parse(dob);
            return java.time.Period.between(date, java.time.LocalDate.now()).getYears();
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseIncome(String income) {
        if (income == null || income.isBlank()) {
            return null;
        }
        String digits = income.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (Exception e) {
            return null;
        }
    }

    private static final class VerificationDecision {
        private final boolean verified;
        private final String reason;

        private VerificationDecision(boolean verified, String reason) {
            this.verified = verified;
            this.reason = reason;
        }

        boolean isVerified() {
            return verified;
        }

        String reason() {
            return reason;
        }

        static VerificationDecision verified() {
            return new VerificationDecision(true, "Verified");
        }

        static VerificationDecision needsReview(String reason) {
            return new VerificationDecision(false, reason);
        }
    }

    private enum AgeRange {
        RANGE_14_17(14, 17),
        RANGE_18_24(18, 24),
        RANGE_25_30(25, 30),
        RANGE_31_PLUS(31, 200);

        private final int min;
        private final int max;

        AgeRange(int min, int max) {
            this.min = min;
            this.max = max;
        }

        boolean contains(int age) {
            return age >= min && age <= max;
        }

        static AgeRange fromLabel(String label) {
            if (label == null) {
                return null;
            }
            return switch (label) {
                case "14-17" -> RANGE_14_17;
                case "18-24" -> RANGE_18_24;
                case "25-30" -> RANGE_25_30;
                case "31+" -> RANGE_31_PLUS;
                default -> null;
            };
        }
    }

    private UploadResult uploadSubmission(String studentId,
                                          CandidateInfo candidateInfo,
                                          DocumentExtractionResult aadhaar,
                                          DocumentExtractionResult pan,
                                          DocumentExtractionResult income,
                                          boolean nameMatch,
                                          String notes,
                                          boolean verified) {
        String candidateName = candidateInfo == null ? "" : candidateInfo.name();
        String blobName = blobStorageService.buildOnboardingBlobName(studentId, candidateName);
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("studentId", studentId);
        payload.put("candidate", candidateInfo);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("verified", verified);
        result.put("notes", notes);
        result.put("nameMatch", nameMatch);
        payload.put("result", result);
        payload.put("aadhaar", aadhaar);
        payload.put("pan", pan);
        payload.put("income", income);
        payload.put("timestamp", java.time.Instant.now().toString());
        String error = blobStorageService.uploadJson(blobName, payload);
        if (error == null || error.isBlank()) {
            return new UploadResult(blobName, "");
        }
        return new UploadResult("", error);
    }

    private record UploadResult(String blobPath, String error) {}
}
