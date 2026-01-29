import streamlit as st
import streamlit.components.v1 as components
import requests
import json
import re
import sqlite3
import csv
from datetime import datetime
from pathlib import Path
import os
import time
import plotly.graph_objects as go
import math
try:
    from streamlit_agraph import agraph, Node, Edge, Config
except Exception:
    agraph = None
    Node = Edge = Config = None


st.set_page_config(
    page_title="MentorBridge",
    page_icon="✨",
    layout="wide",
)

API_URL = "http://localhost:8080/api/advisor/advice"
DB_PATH = "mentor_sessions.db"
DATASET_PATH = Path("src/main/resources/students.csv")
KG_PATH = Path("src/main/resources/knowledge_graph.json")

st.markdown(
    """
    <style>
    @import url('https://fonts.googleapis.com/css2?family=Sora:wght@300;400;600;700&display=swap');
    html, body, [class*="css"] { font-family: 'Sora', sans-serif; }

    div[data-testid="stChatMessageContent"] {
        max-width: 76%;
        border-radius: 16px;
        padding: 0.8rem 1rem;
        box-shadow: 0 6px 16px rgba(0,0,0,0.06);
        line-height: 1.55;
    }
    div[data-testid="stChatMessageUser"] div[data-testid="stChatMessageContent"] {
        margin-left: auto;
        background: #f3f4f8;
        border: 1px solid #e3e7ef;
    }
    div[data-testid="stChatMessageAssistant"] div[data-testid="stChatMessageContent"] {
        margin-right: auto;
        background: #ffffff;
        border: 1px solid #e3eef0;
        border-left: 5px solid #2f80ed;
    }

    .card { background:#fff; border:1px solid #ececec; border-radius:12px; padding:12px 14px; margin:10px 0; box-shadow:0 6px 16px rgba(0,0,0,0.05); }
    .card h4 { margin:0 0 6px 0; }
    .muted { color:#6b7280; }

    div[data-testid="stButton"] > button[kind="primary"] { background:#2f80ed; border-color:#2f80ed; }
    div[data-testid="stButton"] > button[kind="primary"]:hover { background:#1f6fd1; border-color:#1f6fd1; }
    </style>
    """,
    unsafe_allow_html=True,
)

def _databricks_headers(token: str):
    return {"Authorization": f"Bearer {token}"}


def _databricks_statement(workspace_url: str, warehouse_id: str, token: str, statement: str):
    api_url = f"{workspace_url.rstrip('/')}/api/2.0/sql/statements"
    payload = {
        "statement": statement,
        "warehouse_id": warehouse_id,
        "wait_timeout": "10s",
        "disposition": "INLINE",
    }
    response = requests.post(api_url, json=payload, headers=_databricks_headers(token), timeout=30)
    response.raise_for_status()
    return response.json()


def _databricks_poll_statement(workspace_url: str, statement_id: str, token: str, max_attempts: int = 12):
    api_url = f"{workspace_url.rstrip('/')}/api/2.0/sql/statements/{statement_id}"
    for _ in range(max_attempts):
        response = requests.get(api_url, headers=_databricks_headers(token), timeout=30)
        response.raise_for_status()
        payload = response.json()
        state = payload.get("status", {}).get("state")
        if state in {"SUCCEEDED", "FAILED", "CANCELED"}:
            return payload
        time.sleep(1.0)
    return payload


def _databricks_rows_to_dicts(result_payload):
    result = result_payload.get("result", {})
    data = result.get("data_array", [])
    schema = result.get("schema", {}).get("columns", [])
    columns = [col.get("name", f"col_{idx}") for idx, col in enumerate(schema)]
    return [dict(zip(columns, row)) for row in data]


@st.cache_data(show_spinner=False)
def load_students():
    workspace_url = os.getenv("DATABRICKS_WORKSPACE_URL", "").strip()
    token = os.getenv("DATABRICKS_PAT", "").strip()
    warehouse_id = os.getenv("DATABRICKS_WAREHOUSE_ID", "").strip()
    csv_path = os.getenv("DATABRICKS_STUDENTS_CSV_PATH", "").strip()

    if workspace_url and token and warehouse_id and csv_path:
        statement = (
            "SELECT * FROM read_files("
            f"'{csv_path}',"
            " format => 'csv',"
            " header => true"
            ")"
        )
        try:
            payload = _databricks_statement(workspace_url, warehouse_id, token, statement)
            status = payload.get("status", {}).get("state")
            if status in {"PENDING", "RUNNING"}:
                payload = _databricks_poll_statement(workspace_url, payload.get("statement_id", ""), token)
            if payload.get("status", {}).get("state") == "SUCCEEDED":
                return _databricks_rows_to_dicts(payload)
        except Exception:
            pass

    if not DATASET_PATH.exists():
        return []
    with DATASET_PATH.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))

@st.cache_data(show_spinner=False)
def load_kg_rules():
    if not KG_PATH.exists():
        return []
    with KG_PATH.open(encoding="utf-8") as handle:
        return json.load(handle)


def get_student_record(name: str):
    name = (name or "").strip().lower()
    for row in load_students():
        if row.get("name", "").strip().lower() == name:
            return row
    return None


def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db()
    with conn:
        conn.execute(
            "CREATE TABLE IF NOT EXISTS sessions (student_name TEXT PRIMARY KEY, created_at TEXT, updated_at TEXT)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY AUTOINCREMENT, student_name TEXT, role TEXT, content TEXT, created_at TEXT)"
        )
        conn.execute(
            "CREATE TABLE IF NOT EXISTS profiles (student_name TEXT PRIMARY KEY, education_level TEXT, skills TEXT, interests TEXT, language TEXT, "
            "ai_data_interest TEXT, device_access TEXT, time_per_week_hours INTEGER, math_comfort INTEGER, problem_solving_confidence INTEGER, english_comfort INTEGER, updated_at TEXT)"
        )
    conn.close()


def save_message(student_name, role, content):
    if not student_name:
        return
    now = datetime.utcnow().isoformat()
    conn = get_db()
    with conn:
        conn.execute(
            "INSERT INTO messages (student_name, role, content, created_at) VALUES (?, ?, ?, ?)",
            (student_name, role, content, now),
        )
    conn.close()


def load_messages(student_name):
    if not student_name:
        return []
    conn = get_db()
    rows = conn.execute(
        "SELECT role, content FROM messages WHERE student_name=? ORDER BY id ASC", (student_name,)
    ).fetchall()
    conn.close()
    return [{"role": r["role"], "content": r["content"]} for r in rows]


def save_profile(student_name, education_level, skills, interests, language,
                 ai_data_interest, device_access, time_per_week_hours,
                 math_comfort, problem_solving_confidence, english_comfort):
    if not student_name:
        return
    now = datetime.utcnow().isoformat()
    conn = get_db()
    with conn:
        conn.execute(
            "INSERT INTO profiles (student_name, education_level, skills, interests, language, ai_data_interest, device_access, time_per_week_hours, math_comfort, problem_solving_confidence, english_comfort, updated_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
            "ON CONFLICT(student_name) DO UPDATE SET "
            "education_level=excluded.education_level, skills=excluded.skills, interests=excluded.interests, language=excluded.language, "
            "ai_data_interest=excluded.ai_data_interest, device_access=excluded.device_access, time_per_week_hours=excluded.time_per_week_hours, "
            "math_comfort=excluded.math_comfort, problem_solving_confidence=excluded.problem_solving_confidence, english_comfort=excluded.english_comfort, updated_at=excluded.updated_at",
            (student_name, education_level, ",".join(skills), ",".join(interests), language,
             ai_data_interest, device_access, time_per_week_hours, math_comfort, problem_solving_confidence, english_comfort, now),
        )
    conn.close()


def load_profile(student_name):
    if not student_name:
        return None
    conn = get_db()
    row = conn.execute(
        "SELECT education_level, skills, interests, language, ai_data_interest, device_access, time_per_week_hours, math_comfort, problem_solving_confidence, english_comfort FROM profiles WHERE student_name=?",
        (student_name,),
    ).fetchone()
    conn.close()
    if not row:
        return None
    return {
        "education_level": row["education_level"],
        "skills": [s for s in row["skills"].split(",") if s],
        "interests": [i for i in row["interests"].split(",") if i],
        "language": row["language"],
        "ai_data_interest": row["ai_data_interest"],
        "device_access": row["device_access"],
        "time_per_week_hours": row["time_per_week_hours"],
        "math_comfort": row["math_comfort"],
        "problem_solving_confidence": row["problem_solving_confidence"],
        "english_comfort": row["english_comfort"],
    }


def sanitize_choices(values, options):
    if not values:
        return []
    option_set = set(options)
    return [v for v in values if v in option_set]




def compute_radar_scores(skills, interests):
    categories = ["Communication", "Tech Basics", "Problem Solving", "Creativity", "Business/Marketing"]
    mapping = {
        "Communication": "Communication",
        "Teamwork": "Communication",
        "Customer Service": "Communication",
        "Basic Computer Literacy": "Tech Basics",
        "Microsoft Office": "Tech Basics",
        "Typing": "Tech Basics",
        "Python (Basics)": "Problem Solving",
        "Java (Basics)": "Problem Solving",
        "SQL (Basics)": "Problem Solving",
        "Problem Solving": "Problem Solving",
        "Graphic Design (Beginner)": "Creativity",
        "Video Editing (Beginner)": "Creativity",
        "Creative Arts": "Creativity",
        "Marketing": "Business/Marketing",
        "Sales": "Business/Marketing",
        "Social Media Management": "Business/Marketing",
        "Entrepreneurship": "Business/Marketing",
    }
    scores = {c: 0 for c in categories}
    for item in skills + interests:
        mapped = mapping.get(item)
        if mapped:
            scores[mapped] += 1
    max_count = max(1, max(scores.values()))
    scaled = [min(5, max(1, int(round(1 + (scores[c] / max_count) * 4)))) for c in categories]
    return categories, scaled



def render_radar_chart(skills, interests):
    categories, values = compute_radar_scores(skills, interests)
    fig = go.Figure()
    fig.add_trace(go.Scatterpolar(r=values + [values[0]], theta=categories + [categories[0]], fill="toself"))
    fig.update_layout(polar=dict(radialaxis=dict(visible=True, range=[0, 5])), showlegend=False, height=320)
    st.plotly_chart(fig, use_container_width=True)


def render_knowledge_graph(interests, key_suffix="main"):
    if not agraph:
        st.warning("Install streamlit-agraph to view the interactive knowledge graph.")
        return
    rules = load_kg_rules()
    if not rules:
        st.info("Knowledge graph rules not loaded.")
        return
    interest_set = {i.strip() for i in interests}
    nodes = {}
    edges = []

    def upsert_node(node_id, label, color):
        if node_id not in nodes:
            nodes[node_id] = Node(id=node_id, label=label, size=18, color=color)

    for rule in rules:
        interest = rule.get("interest", "")
        trait = rule.get("trait", "")
        skill = rule.get("skill", "")
        role = rule.get("role", "")
        if interest_set and interest not in interest_set:
            continue
        upsert_node(f"interest:{interest}", interest, "#2f80ed")
        upsert_node(f"trait:{trait}", trait, "#1f7a5f")
        upsert_node(f"skill:{skill}", skill, "#f6b445")
        upsert_node(f"role:{role}", role, "#6f42c1")
        edges.append(Edge(source=f"interest:{interest}", target=f"trait:{trait}", label="has core logic"))
        edges.append(Edge(source=f"trait:{trait}", target=f"skill:{skill}", label="builds"))
        edges.append(Edge(source=f"skill:{skill}", target=f"role:{role}", label="required for"))

    config = Config(width=700, height=420, directed=True, physics=True, hierarchical=False)
    agraph(list(nodes.values()), edges, config)


def build_conversation_context(messages, max_turns=6):
    lines = []
    for msg in messages[-max_turns:]:
        role = "User" if msg["role"] == "user" else "Assistant"
        lines.append(f"{role}: {msg['content']}")
    return "\n".join(lines) if lines else "No prior messages."


def call_backend(current_skills, interests, education_level, preferred_language, user_message, conversation_context, roadmap_requested):
    payload = {
        "currentSkills": current_skills,
        "interests": interests,
        "educationLevel": education_level,
        "preferredLanguage": preferred_language,
        "userMessage": user_message,
        "conversationContext": conversation_context,
        "studentName": st.session_state.get("student_name", ""),
        "roadmapRequested": roadmap_requested,
        "aiDataInterest": st.session_state.get("ai_data_interest", ""),
        "deviceAccess": st.session_state.get("device_access", ""),
        "timePerWeekHours": st.session_state.get("time_per_week_hours"),
        "mathComfort": st.session_state.get("math_comfort"),
        "problemSolvingConfidence": st.session_state.get("problem_solving_confidence"),
        "englishComfort": st.session_state.get("english_comfort"),
    }
    return requests.post(API_URL, json=payload, timeout=60)

def render_message(content):
    if not content:
        return
    pattern = re.compile(r"```mermaid\s*(.*?)```", re.DOTALL | re.IGNORECASE)
    parts = pattern.split(content)
    if len(parts) == 1:
        st.markdown(content)
        return

    counter = st.session_state.get("mermaid_counter", 0)
    for idx, part in enumerate(parts):
        if idx % 2 == 0:
            if part.strip():
                st.markdown(part)
            continue

        counter += 1
        diagram_id = f"mermaid-{counter}"
        components.html(
            f"""
            <div id="{diagram_id}" class="mermaid">
            {part}
            </div>
            <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
            <script>
              mermaid.initialize({{ startOnLoad: true }});
            </script>
            """,
            height=420,
        )
    st.session_state["mermaid_counter"] = counter



def send_message(user_text, selected_skills, selected_interests, education_level, language_choice, roadmap_requested=False):
    st.session_state.messages.append({"role": "user", "content": user_text})
    save_message(st.session_state.get("student_name", ""), "user", user_text)
    save_profile(
        st.session_state.get("student_name", ""),
        education_level,
        selected_skills,
        selected_interests,
        language_choice,
        st.session_state.get("ai_data_interest"),
        st.session_state.get("device_access"),
        st.session_state.get("time_per_week_hours"),
        st.session_state.get("math_comfort"),
        st.session_state.get("problem_solving_confidence"),
        st.session_state.get("english_comfort"),
    )
    with st.chat_message("user"):
        st.markdown(user_text)

    with st.chat_message("assistant"):
        with st.spinner("Thinking..."):
            response = call_backend(", ".join(selected_skills), ", ".join(selected_interests), education_level, language_choice,
                                    user_text, build_conversation_context(st.session_state.messages), roadmap_requested)
            if response.status_code == 200:
                advice = response.text
                st.session_state.messages.append({"role": "assistant", "content": advice})
                save_message(st.session_state.get("student_name", ""), "assistant", advice)
                render_message(advice)
            else:
                st.error(f"Server Error ({response.status_code}): {response.text}")


init_db()
if "student_name" not in st.session_state:
    st.session_state.student_name = ""
if "messages" not in st.session_state:
    st.session_state.messages = []


SKILL_OPTIONS = ["Communication", "Basic Computer Literacy", "Microsoft Office", "Typing", "Teamwork",
         "Problem Solving", "Customer Service", "Sales", "Data Entry", "Python (Basics)", "Java (Basics)", "SQL (Basics)",
         "HTML/CSS (Basics)", "Video Editing (Beginner)", "Social Media Management", "Marketing", "Graphic Design (Beginner)"]

INTEREST_OPTIONS = ["Technology", "Gaming", "Creative Arts", "Social Media", "Entrepreneurship",
         "Helping Others", "Music", "Movies", "Sports", "Writing", "Reading"]

with st.sidebar:
    st.session_state["selected_skills"] = sanitize_choices(st.session_state.get("selected_skills", []), SKILL_OPTIONS)
    st.session_state["selected_interests"] = sanitize_choices(st.session_state.get("selected_interests", []), INTEREST_OPTIONS)

    st.markdown("**MentorBridge**")
    st.header("Mentor Workspace")
    st.info("Select a learner and guide them with the mentor copilot.")

    students = load_students()
    student_names = sorted({row.get("name", "").strip() for row in students if row.get("name")})
    student_choice = st.selectbox("Learner", [""] + student_names)

    if student_choice:
        st.session_state.student_name = student_choice
        profile = load_profile(student_choice) or {}
        dataset = get_student_record(student_choice) or {}
        st.session_state.education_level = profile.get("education_level") or dataset.get("education_level") or "10th Pass"
        st.session_state.selected_skills = profile.get("skills") or [s.strip() for s in dataset.get("skills", "").split(",") if s.strip()]
        st.session_state.selected_interests = profile.get("interests") or [s.strip() for s in dataset.get("interests", "").split(",") if s.strip()]
        st.session_state.ai_data_interest = profile.get("ai_data_interest") or "High"
        st.session_state.device_access = profile.get("device_access") or "Smartphone"
        st.session_state.time_per_week_hours = profile.get("time_per_week_hours") or 6
        st.session_state.math_comfort = profile.get("math_comfort") or 3
        st.session_state.problem_solving_confidence = profile.get("problem_solving_confidence") or 3
        st.session_state.english_comfort = profile.get("english_comfort") or 3
        st.session_state.messages = [{"role": "assistant", "content": "Mentor copilot is ready. Ask a question."}]

    st.markdown("---")
    language_choice = st.radio("Language", ["English", "Hindi"], horizontal=True, key="language_choice")

    st.markdown("### Learner Skills & Interests")
    st.multiselect(
        "Skills",
        SKILL_OPTIONS,
        default=sanitize_choices(st.session_state.get("selected_skills", []), SKILL_OPTIONS),
        key="selected_skills"
    )
    st.multiselect(
        "Interests",
        INTEREST_OPTIONS,
        default=sanitize_choices(st.session_state.get("selected_interests", []), INTEREST_OPTIONS),
        key="selected_interests"
    )

    st.markdown("### Learning Context")
    st.selectbox("AI/Data Interest", ["Low", "Medium", "High"], key="ai_data_interest")
    st.selectbox("Device Access", ["Smartphone", "Laptop", "Shared Device", "Cyber Cafe"], key="device_access")
    st.slider("Time per Week (hours)", 1, 40, key="time_per_week_hours")

    st.markdown("### Self-Assessment")
    st.slider("Math Comfort (1-5)", 1, 5, key="math_comfort")
    st.slider("Problem Solving (1-5)", 1, 5, key="problem_solving_confidence")
    st.slider("English Comfort (1-5)", 1, 5, key="english_comfort")

    if st.button("Save Learner Updates"):
        save_profile(
            st.session_state.get("student_name", ""),
            st.session_state.get("education_level", "10th Pass"),
            st.session_state.get("selected_skills", []),
            st.session_state.get("selected_interests", []),
            language_choice,
            st.session_state.get("ai_data_interest"),
            st.session_state.get("device_access"),
            st.session_state.get("time_per_week_hours"),
            st.session_state.get("math_comfort"),
            st.session_state.get("problem_solving_confidence"),
            st.session_state.get("english_comfort"),
        )

st.title("MentorBridge")
st.caption("MentorBridge: AI mentor copilot for AI/Data onboarding and guidance")

left, right = st.columns(2)
with left:
    st.markdown("<div class='card'><h4>Skill Bridge Radar</h4><div class='muted'>Baseline vs current strengths for the selected learner.</div></div>", unsafe_allow_html=True)
    render_radar_chart(st.session_state.get("selected_skills", []), st.session_state.get("selected_interests", []))

st.caption("Download radar as PNG (from browser toolbar) or screenshot.")


with right:
    st.markdown("<div class='card'><h4>Knowledge Graph Explorer</h4><div class='muted'>Relational paths from interests to roles.</div></div>", unsafe_allow_html=True)
    render_knowledge_graph(st.session_state.get("selected_interests", []))

st.markdown("---")



for msg in st.session_state.messages:
    with st.chat_message(msg["role"]):
        render_message(msg["content"])

if st.button("Generate Mentor Roadmap", type="primary"):
    send_message(
        "Generate a full mentor-ready roadmap for this learner.",
        st.session_state.get("selected_skills", []),
        st.session_state.get("selected_interests", []),
        st.session_state.get("education_level", "10th Pass"),
        language_choice,
        roadmap_requested=True,
    )

followup = st.chat_input("Ask the mentor copilot to refine or draft guidance...")
if followup:
    send_message(
        followup,
        st.session_state.get("selected_skills", []),
        st.session_state.get("selected_interests", []),
        st.session_state.get("education_level", "10th Pass"),
        language_choice,
        roadmap_requested=False,
    )
