---
title: "MentorBridge"
archetype: "onboarding_guide"
status: "Internal (Hackathon / Learning Prototype)"
owner: "Team MentorBridge"
maintainer: "Team MentorBridge"
version: "0.3"
last_reviewed: "2026-01-29"
tags: ["azure", "rag", "knowledge-graph", "mentoring", "onboarding", "databricks"]
---

# MentorBridge (Graph-RAG Mentor Copilot)

This is a hands-on prototype inspired by a hackathon use case. It is not a production product.

The goal is to demonstrate how Azure AI services, Databricks, and a lightweight knowledge graph can:
- support mentors with structured guidance
- accelerate onboarding with document verification
- keep humans in control for low-confidence cases

## What Is In This App

### UI pages (Streamlit)
- **Chat** (Chat.py): candidate-facing chat with Magic Bus branding.
- **Onboarding** (pages/Onboarding.py): document upload and verification.
- **Mentor Bridge** (pages/MentorBridge.py): mentor-facing copilot workspace.

### Backend (Spring Boot)
- `POST /api/advisor/advice` for mentor or candidate guidance
- `POST /api/onboarding/verify` for document verification and blob storage

### AI services
- Azure OpenAI: chat and reasoning
- Azure Document Intelligence: Aadhaar and income extraction

### Storage
- Azure Blob Storage: onboarding payloads (JSON)
- SQLite: mentor chat sessions (local demo persistence)
- Databricks Vector Search: RAG for similar student context

## System Architecture (Simple View)

- Streamlit UI -> Spring Boot API
- Spring Boot -> Azure OpenAI for chat responses
- Spring Boot -> Azure Document Intelligence for doc extraction
- Spring Boot -> Azure Blob Storage for onboarding payloads
- Spring Boot -> Databricks Vector Search for RAG context

## Quickstart (Local)

### 1) Backend
```
cd C:\Users\ALayek\Downloads\graph-rag-mentor-copilot
mvn -q spring-boot:run
```

### 2) Frontend
```
streamlit run Chat.py
```

### 3) UI dependencies
```
pip install plotly streamlit-agraph
```

## Environment Variables

### Azure OpenAI
```
AZURE_OPENAI_ENDPOINT=https://<your-resource>.openai.azure.com/
AZURE_OPENAI_API_KEY=<your-key>
AZURE_OPENAI_CHAT_DEPLOYMENT=gpt-4o
AZURE_OPENAI_EMBEDDING_DEPLOYMENT=text-embedding-ada-002
```

### Azure Document Intelligence
```
AZURE_DOCINTEL_ENDPOINT=https://<your-resource>.cognitiveservices.azure.com/
AZURE_DOCINTEL_KEY=<your-key>
```

### Azure Blob Storage
```
AZURE_STORAGE_CONNECTION_STRING=<your-connection-string>
AZURE_STORAGE_CONTAINER=forms
```

### Databricks (RAG + CSV lookup)
```
DATABRICKS_WORKSPACE_URL=https://adb-<workspace>.azuredatabricks.net
DATABRICKS_PAT=<your-databricks-pat>
DATABRICKS_VECTOR_SEARCH_INDEX=<catalog>.<schema>.<index_name>
DATABRICKS_VECTOR_SEARCH_TEXT_COLUMN=text
DATABRICKS_WAREHOUSE_ID=<sql-warehouse-id>
DATABRICKS_STUDENTS_CSV_PATH=/Volumes/<catalog>/<schema>/<volume>/<path>/students.csv
```

### Demo switches (optional)
```
ONBOARDING_DEMO_MODE=false
ONBOARDING_DEMO_SKIP_RULES=true
```

## Onboarding Verification Logic

The onboarding flow does the following:
1) Extracts fields from Aadhaar and income documents.
2) Checks confidence thresholds.
3) Validates age (14-25) and income (<= 5 lakhs) if available.
4) Uploads a JSON payload to Blob Storage with extracted fields and decision.

If `ONBOARDING_DEMO_SKIP_RULES=true`, verification rules are bypassed for demo purposes.

## Blob Storage Output

Each onboarding submission is stored as:
```
onboarding/YYYY/MM/DD/student-<studentId>-<candidate-name>-<timestamp>.json
```

## Databricks Usage

- Vector Search is used for RAG context in mentor or candidate chat.
- Optional: `pages/MentorBridge.py` can read `students.csv` directly from a Databricks volume when
  `DATABRICKS_WAREHOUSE_ID` and `DATABRICKS_STUDENTS_CSV_PATH` are set.

## Project Structure

- `Chat.py` - candidate chat UI
- `pages/Onboarding.py` - onboarding UI
- `pages/MentorBridge.py` - mentor copilot UI
- `src/main/java/...` - Spring Boot backend
- `src/main/resources/students.csv` - demo data
- `src/main/resources/knowledge_graph.json` - interest to skill to role rules

## Mermaid Diagrams

Mermaid flowcharts are rendered in the chat UI using a custom renderer. If you see raw code blocks,
restart Streamlit after changes:
```
streamlit run Chat.py
```

## Notes

- This is a prototype for demo and learning purposes.
- No model fine-tuning is performed.
- Human oversight remains in the loop for low-confidence cases.
