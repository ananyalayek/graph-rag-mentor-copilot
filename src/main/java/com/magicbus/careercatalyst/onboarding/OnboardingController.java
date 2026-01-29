package com.magicbus.careercatalyst.onboarding;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/verify")
    public OnboardingResult verify(
            @RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam("aadhaar") MultipartFile aadhaar,
            @RequestParam("income") MultipartFile income,
            @RequestParam(value = "candidateName", required = false) String candidateName,
            @RequestParam(value = "candidateAgeRange", required = false) String candidateAgeRange,
            @RequestParam(value = "candidateEducation", required = false) String candidateEducation,
            @RequestParam(value = "candidateLocation", required = false) String candidateLocation,
            @RequestParam(value = "candidateInterests", required = false) String candidateInterests,
            @RequestParam(value = "candidateSkills", required = false) String candidateSkills,
            @RequestParam(value = "candidateLanguage", required = false) String candidateLanguage) {
        CandidateInfo candidateInfo = new CandidateInfo(
                candidateName,
                candidateAgeRange,
                candidateEducation,
                candidateLocation,
                candidateInterests,
                candidateSkills,
                candidateLanguage
        );
        return onboardingService.verify(studentId, aadhaar, null, income, candidateInfo);
    }
}
