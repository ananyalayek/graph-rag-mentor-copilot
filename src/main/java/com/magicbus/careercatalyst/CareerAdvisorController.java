
package com.magicbus.careercatalyst;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/advisor")
public class CareerAdvisorController {

    private final CareerAdvisorService careerAdvisorService;

    @Autowired
    public CareerAdvisorController(CareerAdvisorService careerAdvisorService) {
        this.careerAdvisorService = careerAdvisorService;
    }

    @PostMapping("/advice")
    public String getAdvice(@RequestBody StudentProfileRequest request) {
        return careerAdvisorService.getCareerAdvice(
            request.getCurrentSkills(),
            request.getInterests(),
            request.getEducationLevel(),
            request.getPreferredLanguage(),
            request.getUserMessage(),
            request.getConversationContext(),
            request.getStudentName(),
            Boolean.TRUE.equals(request.getRoadmapRequested()),
            request.getAiDataInterest(),
            request.getDeviceAccess(),
            request.getTimePerWeekHours(),
            request.getMathComfort(),
            request.getProblemSolvingConfidence(),
            request.getEnglishComfort()
        );
    }
}
