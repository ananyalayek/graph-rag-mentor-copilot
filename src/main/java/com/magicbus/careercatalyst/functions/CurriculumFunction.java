package com.magicbus.careercatalyst.functions;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// FIX: We give this Configuration a specific name so it doesn't grab the "curriculumFunction" name
@Configuration("curriculumConfig")
public class CurriculumFunction {

    /**
     * Mock Service to represent the mentor curriculum database.
     * We make this a separate class or Bean so it can be injected.
     */
    public static class CurriculumService {
        // Mock database of modules for different career paths
        private static final Map<String, List<String>> CURRICULUM_DB = Map.of(
            "Data Analyst", List.of("Module 1: Introduction to Data", "Module 2: Excel for Data Analysis", "Module 3: SQL Fundamentals", "Module 4: Power BI Basics"),
            "Frontend Developer", List.of("Module 1: HTML & CSS", "Module 2: JavaScript Basics", "Module 3: Introduction to React", "Module 4: Building a Portfolio Project"),
            "Cloud Support Associate", List.of("Module 1: Introduction to Cloud Computing", "Module 2: AWS Core Services", "Module 3: Networking and Security Basics", "Module 4: Troubleshooting Common Issues"),
            "Digital Marketer", List.of("Module 1: SEO Fundamentals", "Module 2: Social Media Marketing", "Module 3: Content Creation", "Module 4: Google Analytics"),
            // Fallback for generic queries
            "General", List.of("Module 1: Digital Literacy", "Module 2: Soft Skills", "Module 3: Professional Communication")
        );

        public List<String> getModules(String careerPath) {
            // Simple fuzzy matching to handle inputs like "I want to be a Data Analyst"
            return CURRICULUM_DB.entrySet().stream()
                .filter(entry -> careerPath.toLowerCase().contains(entry.getKey().toLowerCase()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(CURRICULUM_DB.get("General"));
        }
    }

    // 1. Register the Service as a Bean so Spring can find it
    @Bean
    public CurriculumService curriculumService() {
        return new CurriculumService();
    }

    @Bean
    public CurriculumTool curriculumTool(CurriculumService curriculumService) {
        return new CurriculumTool(curriculumService);
    }

    public static class CurriculumTool {
        private final CurriculumService curriculumService;

        public CurriculumTool(CurriculumService curriculumService) {
            this.curriculumService = curriculumService;
        }

        @Tool(name = "curriculumFunction",
              description = "Get the list of curriculum modules for a specific IT career path from the mentor curriculum database.")
        public List<String> curriculumFunction(String careerPath) {
            return curriculumService.getModules(careerPath);
        }
    }
}
