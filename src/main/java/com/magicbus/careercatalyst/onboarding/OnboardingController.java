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
            @RequestParam("studentId") String studentId,
            @RequestParam("aadhaar") MultipartFile aadhaar,
            @RequestParam("pan") MultipartFile pan,
            @RequestParam("income") MultipartFile income) {
        return onboardingService.verify(studentId, aadhaar, pan, income);
    }
}
