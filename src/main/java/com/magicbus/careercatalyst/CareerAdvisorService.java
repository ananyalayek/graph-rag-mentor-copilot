package com.magicbus.careercatalyst;

import com.magicbus.careercatalyst.functions.CurriculumFunction.CurriculumTool;
import com.magicbus.careercatalyst.databricks.DatabricksVectorSearchClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class CareerAdvisorService {

    private final ChatClient chatClient;
    private final DatabricksVectorSearchClient vectorSearchClient;
    // We inject the Tool metadata so the AI knows it can use it
    private final CurriculumTool curriculumTool;
    private final String knowledgeGraphRules;

    public CareerAdvisorService(ChatClient.Builder chatClientBuilder,
                                DatabricksVectorSearchClient vectorSearchClient,
                                CurriculumTool curriculumTool) {
        this.vectorSearchClient = vectorSearchClient;
        this.curriculumTool = curriculumTool;
        this.knowledgeGraphRules = loadKnowledgeGraphRules();
        // Build the client with default tools enabled
        this.chatClient = chatClientBuilder
                .defaultTools(curriculumTool)
                .build();
    }

    private String loadKnowledgeGraphRules() {
        try {
            ClassPathResource resource = new ClassPathResource("knowledge_graph.json");
            String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, String>> rules = mapper.readValue(json, List.class);
            List<String> lines = new ArrayList<>();
            for (Map<String, String> rule : rules) {
                String interest = rule.getOrDefault("interest", "Unknown");
                String trait = rule.getOrDefault("trait", "Unknown");
                String skill = rule.getOrDefault("skill", "Unknown");
                String role = rule.getOrDefault("role", "Unknown");
                lines.add("Interest: " + interest + " -> Trait: " + trait + " -> Skill: " + skill + " -> Role: " + role);
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            return "Knowledge graph rules not available.";
        }
    }

    /**
     * Generates a rich, personalized career roadmap using RAG, Tool Calling, and Visual Prompting.
     * * @param currentSkills User's existing skills
     * @param interests User's passions
     * @param educationLevel Current education status
     * @param preferredLanguage "English", "Hindi", or "Marathi"
     * @param userMessage Latest user message for multi-turn chat
     * @param conversationContext Prior messages (recent history)
     * @param studentName Student display name
     * @param roadmapRequested Whether user requested a full roadmap
     * @param aiDataInterest AI/Data interest level
     * @param deviceAccess Device access type
     * @param timePerWeekHours Time available per week
     * @param mathComfort Math comfort rating (1-5)
     * @param problemSolvingConfidence Problem solving confidence rating (1-5)
     * @param englishComfort English comfort rating (1-5)
     * @return Markdown response with Mermaid charts and Citations
     */
    public String getCareerAdvice(String currentSkills,
                                  String interests,
                                  String educationLevel,
                                  String preferredLanguage,
                                  String userMessage,
                                  String conversationContext,
                                  String studentName,
                                  boolean roadmapRequested,
                                  String aiDataInterest,
                                  String deviceAccess,
                                  Integer timePerWeekHours,
                                  Integer mathComfort,
                                  Integer problemSolvingConfidence,
                                  Integer englishComfort) {

        // --- STEP 1: RAG RETRIEVAL (The Memory) ---
        // We search the vector database for 3 students with similar profiles.
        List<Document> similarProfiles = vectorSearchClient
                .similaritySearch(currentSkills + " " + interests, 3);

        String ragContext = similarProfiles.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // --- STEP 2: LANGUAGE LOGIC (The Empathy) ---
        String languageInstruction = "English (Professional but simple). Respond only in English.";
        if ("Hindi".equalsIgnoreCase(preferredLanguage)) {
            languageInstruction = "Hindi (Simple, friendly, and encouraging). Respond only in Hindi. Do not mix English.";
        } else if ("Marathi".equalsIgnoreCase(preferredLanguage)) {
            languageInstruction = "Marathi (Simple, friendly, and encouraging). Respond only in Marathi. Do not mix English.";
        }

        String candidateStyle = """
                You are a Magic Bus mentor copilot assisting a mentor.
                Speak to the mentor (not the student). Use a concise, professional, and empathetic tone.
                Your job: interpret the student's context, suggest what the mentor should ask next, and generate a roadmap the mentor can share.
                Include actionable guidance that helps the mentor support the student effectively.
                Do not address the student directly.
                """;

        String discoveryPrompt = """
                %s
                USER PROFILE:
                - Name: {student_name}
                - Education: {education}
                - Skills: {skills}
                - Interests: {interests}
                - AI/Data Interest: {ai_interest}
                - Device Access: {device_access}
                - Time per Week: {time_per_week}
                - Math Comfort (1-5): {math_comfort}
                - Problem Solving Confidence (1-5): {problem_solving}
                - English Comfort (1-5): {english_comfort}
                - Language Mode: {language}

                --- SUCCESS STORIES (Context from Database) ---
                {rag_context}
                -----------------------------------------------

                --- CAREER KNOWLEDGE GRAPH (Relational Rules) ---
                {kg_rules}
                -----------------------------------------------

                --- RECENT CONVERSATION ---
                {conversation_context}
                ---------------------------

                LATEST USER MESSAGE:
                {user_message}

                YOUR MISSION:
                1) Briefly summarize the candidate context for the mentor.
                2) Provide 4-6 focused follow-up questions the mentor should ask (goals, location, time available, device/internet access, preferred learning style).
                3) Offer 2-3 possible Magic Bus program directions or focus areas based on the profile.
                4) Suggest a next-step roadmap outline the mentor can share.
                End with one supportive line addressed to the mentor in the selected language.
                """.formatted(candidateStyle);

        String roadmapPrompt = """
                %s
                USER PROFILE:
                - Name: {student_name}
                - Education: {education}
                - Skills: {skills}
                - Interests: {interests}
                - AI/Data Interest: {ai_interest}
                - Device Access: {device_access}
                - Time per Week: {time_per_week}
                - Math Comfort (1-5): {math_comfort}
                - Problem Solving Confidence (1-5): {problem_solving}
                - English Comfort (1-5): {english_comfort}
                - Language Mode: {language}

                --- SUCCESS STORIES (Context from Database) ---
                {rag_context}
                -----------------------------------------------

                --- CAREER KNOWLEDGE GRAPH (Relational Rules) ---
                {kg_rules}
                -----------------------------------------------

                --- RECENT CONVERSATION ---
                {conversation_context}
                ---------------------------

                LATEST USER MESSAGE:
                {user_message}

                YOUR MISSION:
                Create a high-impact, visual career roadmap for the candidate to follow.
                Follow this structure strictly and keep it readable:

                USER PROFILE:
                - Name: {student_name}
                - Education: {education}
                - Skills: {skills}
                - Interests: {interests}
                - Language Mode: {language}

                --- SUCCESS STORIES (Context from Database) ---
                {rag_context}
                -----------------------------------------------

                --- RECENT CONVERSATION ---
                {conversation_context}
                ---------------------------

                LATEST USER MESSAGE:
                {user_message}

                YOUR MISSION:
                Create a high-impact, visual career roadmap. Follow this structure strictly and keep it readable:

                ### 1. Recommended Paths 🎯
                Suggest 2 distinct IT career paths, prioritizing AI/ML or AI-adjacent roles if the candidate is interested.
                * Explain WHY in a friendly candidate-facing tone (use {language} for the example line).
                * Add a short summary line the candidate can act on this week.
                * Use the 'curriculumFunction' tool to fetch and list the specific training modules for these paths.

                ### 2. Success-Twin (Relatability Anchor) 🤝
                Pick one real student name from the SUCCESS STORIES and tell a 2-3 line mini-story.
                Use the real name from the data (do not anonymize).

                ### 3. Skill Bridge (Micro-Skill Mapping) 🧠
                Identify 1-2 "hidden skills" based on the user's interests or hobbies and connect them to a career path.
                Use a simple bridge sentence like: "Because you enjoy X, you already have Y, which helps in Z."

                ### 4. Social Proof (Citations) 🔍
                You MUST mention specific students from the SUCCESS STORIES above.
                (e.g., "Just like **Student #12 (Rohan)** who also liked Gaming...")
                This builds trust.

                ### 5. Visual Career Map 🗺️
                Generate a **Mermaid.js** flowchart syntax to visualize the journey.
                **IMPORTANT:** Wrap the code in a block like:
                ```mermaid
                flowchart TB
                    A[Current - {education}] --> B[Step 1 - Skill Up]
                    B --> C[Step 2 - First Project]
                    C --> D[Step 3 - Build Portfolio]
                    D --> E[Step 4 - Apply for Jobs]
                    E --> F[Goal - Tech Professional]
                    style A fill:#e6f0f3,stroke:#1f7a5f,stroke-width:1px
                    style F fill:#ffe2b3,stroke:#c26a00,stroke-width:2px
                ```

                ### 6. Immediate Action Plan 🎒
                Create a Markdown Table with 3 columns:
                | Step | Free Resource (YouTube/Website) | Time |

                (Suggest real resources like 'CodeWithHarry', 'FreeCodeCamp', 'Khan Academy').

                ### 7. Extra Resources 🔗
                Provide 2-3 links students can explore next (include full URLs).

                End with a short, motivating punchline in {language}.
                """.formatted(candidateStyle);

        String promptBody = roadmapRequested ? roadmapPrompt : discoveryPrompt;
        PromptTemplate promptTemplate = new PromptTemplate(promptBody);

        // --- STEP 4: EXECUTION ---
        String safeConversationContext = conversationContext == null ? "No prior messages." : conversationContext;
        String safeUserMessage = userMessage == null ? "No new message. Generate the first roadmap." : userMessage;
        String safeStudentName = studentName == null || studentName.isBlank() ? "Student" : studentName;
        String safeAiInterest = aiDataInterest == null || aiDataInterest.isBlank() ? "Not shared" : aiDataInterest;
        String safeDeviceAccess = deviceAccess == null || deviceAccess.isBlank() ? "Not shared" : deviceAccess;
        String safeTimePerWeek = timePerWeekHours == null ? "Not shared" : timePerWeekHours + " hours/week";
        String safeMathComfort = mathComfort == null ? "Not shared" : mathComfort.toString();
        String safeProblemSolving = problemSolvingConfidence == null ? "Not shared" : problemSolvingConfidence.toString();
        String safeEnglishComfort = englishComfort == null ? "Not shared" : englishComfort.toString();

        Map<String, Object> promptVars = new java.util.HashMap<>();
        promptVars.put("education", educationLevel);
        promptVars.put("skills", currentSkills);
        promptVars.put("interests", interests);
        promptVars.put("language", languageInstruction);
        promptVars.put("rag_context", ragContext);
        promptVars.put("conversation_context", safeConversationContext);
        promptVars.put("user_message", safeUserMessage);
        promptVars.put("student_name", safeStudentName);
        promptVars.put("ai_interest", safeAiInterest);
        promptVars.put("device_access", safeDeviceAccess);
        promptVars.put("time_per_week", safeTimePerWeek);
        promptVars.put("math_comfort", safeMathComfort);
        promptVars.put("problem_solving", safeProblemSolving);
        promptVars.put("english_comfort", safeEnglishComfort);

        promptVars.put("kg_rules", knowledgeGraphRules);
        Prompt prompt = promptTemplate.create(promptVars);

        // We call the model. The model will automatically detect if it needs to call
        // the 'curriculumFunction' tool to fill in the "Training Modules" section.
        return chatClient.prompt(prompt).call().content();
    }
}
