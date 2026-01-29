import os
import re
import streamlit as st
import streamlit.components.v1 as components
import requests

st.set_page_config(
    page_title="Magic Bus | MentorBridge",
    page_icon="--",
    layout="wide",
)

API_URL = os.getenv("API_URL", "http://localhost:8080/api/advisor/advice")

st.markdown(
    """
<style>
@import url('https://fonts.googleapis.com/css2-family=Fraunces:wght@600;700&family=Work+Sans:wght@300;400;500;600&display=swap');

:root {
  --mb-navy: #0b1f2a;
  --mb-teal: #0f766e;
  --mb-orange: #f47b20;
  --mb-sand: #fff7ed;
  --mb-ink: #16202a;
}

html, body, [class*="css"] {
  font-family: 'Work Sans', sans-serif;
  color: var(--mb-ink);
}

.app-hero {
  background: radial-gradient(1200px 500px at 15% 10%, rgba(244, 123, 32, 0.18), transparent),
              radial-gradient(900px 480px at 85% 5%, rgba(15, 118, 110, 0.18), transparent),
              linear-gradient(180deg, #ffffff 0%, #f9fafb 100%);
  border: 1px solid #eef2f6;
  border-radius: 24px;
  padding: 32px 36px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.08);
}

.hero-title {
  font-family: 'Fraunces', serif;
  font-size: 2.6rem;
  line-height: 1.08;
  margin-bottom: 14px;
}

.hero-kicker {
  color: var(--mb-teal);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-size: 0.82rem;
}

.hero-subtitle {
  font-size: 1.05rem;
  line-height: 1.7;
  margin: 12px 0 18px;
}

.stat-card {
  background: #ffffff;
  border: 1px solid #edf1f6;
  border-radius: 16px;
  padding: 16px 18px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.06);
}

.stat-number {
  font-family: 'Fraunces', serif;
  font-size: 1.4rem;
  color: var(--mb-navy);
}

.pill {
  background: var(--mb-sand);
  border: 1px solid rgba(244, 123, 32, 0.2);
  color: var(--mb-orange);
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 600;
  font-size: 0.8rem;
}

.section-title {
  font-family: 'Fraunces', serif;
  font-size: 1.6rem;
  margin-bottom: 12px;
}

.section-card {
  background: #ffffff;
  border: 1px solid #eef2f6;
  border-radius: 18px;
  padding: 20px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.06);
}

.chat-shell {
  background: #ffffff;
  border: 1px solid #eef2f6;
  border-radius: 18px;
  padding: 18px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.05);
}

div[data-testid="stChatMessageContent"] {
  max-width: 78%;
  border-radius: 16px;
  padding: 0.85rem 1rem;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.06);
  line-height: 1.6;
}

div[data-testid="stChatMessageUser"] div[data-testid="stChatMessageContent"] {
  margin-left: auto;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

div[data-testid="stChatMessageAssistant"] div[data-testid="stChatMessageContent"] {
  margin-right: auto;
  background: #ffffff;
  border: 1px solid #e3f2f0;
  border-left: 4px solid var(--mb-teal);
}

div[data-testid="stButton"] > button[kind="primary"] {
  background: var(--mb-orange);
  border-color: var(--mb-orange);
  font-weight: 600;
}

div[data-testid="stButton"] > button[kind="primary"]:hover {
  background: #df6e1b;
  border-color: #df6e1b;
}

a.mb-link {
  color: var(--mb-teal);
  text-decoration: none;
  font-weight: 600;
}

@media (max-width: 900px) {
  .hero-title { font-size: 2rem; }
}

</style>
""",
    unsafe_allow_html=True,
)

if "messages" not in st.session_state:
    st.session_state.messages = [
        {
            "role": "assistant",
            "content": (
                "Hi! I'm your Magic Bus guide. I can share programs, pathways, and next steps to help you "
                "move from education to employability. What would you like to explore today-"
            ),
        }
    ]

if "candidate_profile" not in st.session_state:
    st.session_state.candidate_profile = {
        "name": "",
        "age_range": "18-24",
        "education": "",
        "location": "",
        "interests": [],
        "skills": [],
        "language": "English",
    }


def build_conversation_context(messages, max_turns=6):
    lines = []
    for msg in messages[-max_turns:]:
        role = "User" if msg["role"] == "user" else "Assistant"
        lines.append(f"{role}: {msg['content']}")
    return "\n".join(lines) if lines else "No prior messages."


def sync_candidate_profile():
    profile = {
        "name": st.session_state.get("candidate_name", ""),
        "age_range": st.session_state.get("candidate_age", ""),
        "education": st.session_state.get("candidate_education", ""),
        "location": st.session_state.get("candidate_location", ""),
        "interests": st.session_state.get("candidate_interests", []),
        "skills": st.session_state.get("candidate_skills", []),
        "language": st.session_state.get("candidate_language", "English"),
    }
    st.session_state.candidate_profile = profile
    return profile


def call_backend(user_text):
    profile = sync_candidate_profile()
    payload = {
        "currentSkills": ", ".join(profile.get("skills", [])),
        "interests": ", ".join(profile.get("interests", [])),
        "educationLevel": profile.get("education", ""),
        "preferredLanguage": profile.get("language", "English"),
        "userMessage": user_text,
        "conversationContext": build_conversation_context(st.session_state.messages),
        "studentName": profile.get("name", ""),
        "roadmapRequested": False,
        "aiDataInterest": "",
        "deviceAccess": "",
        "timePerWeekHours": None,
        "mathComfort": None,
        "problemSolvingConfidence": None,
        "englishComfort": None,
    }
    try:
        return requests.post(API_URL, json=payload, timeout=60)
    except requests.exceptions.RequestException:
        return None


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

try:
    st.markdown("<div class='app-hero' id='hero'>", unsafe_allow_html=True)
    left, right = st.columns([1.2, 0.8], gap="large")

    with left:
        st.markdown("<div class='hero-kicker'>Magic Bus India Foundation</div>", unsafe_allow_html=True)
        st.markdown(
            "<div class='hero-title'>From Childhood to Livelihood - one guided step at a time.</div>",
            unsafe_allow_html=True,
        )
        st.markdown(
            "<div class='hero-subtitle'>"
            "Magic Bus helps young people build life skills, confidence, and employability. "
            "Use this space to explore programmes, ask questions, and get guidance on how to register."
            "</div>",
            unsafe_allow_html=True,
        )
        st.markdown("<span class='pill'>Candidate Support</span>", unsafe_allow_html=True)

    with right:
        st.markdown(
            """
            <div class='stat-card'>
                <div class='stat-number'>Life skills + employability</div>
                <div>Training, mentoring, and pathways to sustainable livelihoods.</div>
            </div>
            <div style='height:12px'></div>
            <div class='stat-card'>
                <div class='stat-number'>Programs across India</div>
                <div>Adolescent and Livelihood programmes supporting youth journeys.</div>
            </div>
            <div style='height:12px'></div>
            <div class='stat-card'>
                <div class='stat-number'>Ready to start-</div>
                <div>Ask questions below or reach out for registration support.</div>
            </div>
            """,
            unsafe_allow_html=True,
        )

    st.markdown("</div>", unsafe_allow_html=True)

    # Force the viewport to stay at the hero after rerenders.
    components.html(
        """
        <script>
        (function() {
          function resetScroll() {
            window.scrollTo(0, 0);
            if (document.activeElement) { document.activeElement.blur(); }
          }
          resetScroll();
          setTimeout(resetScroll, 50);
          setTimeout(resetScroll, 200);
        })();
        </script>
        """,
        height=0,
        width=0,
    )

    st.markdown("\n")

    st.markdown("<div class='section-card'>", unsafe_allow_html=True)
    st.markdown("<div class='section-title'>What you can do here</div>", unsafe_allow_html=True)
    st.markdown(
        """
        - Learn about Magic Bus programmes and outcomes.
        - Ask questions about eligibility and next steps.
        - Get guidance on how to register.
        """
    )
    st.markdown(
        "Need direct support? Visit the official website at "
        "<a class='mb-link' href='https://www.magicbus.org' target='_blank'>magicbus.org</a>.",
        unsafe_allow_html=True,
    )
    st.markdown("### Preferred Language")
    st.selectbox("Choose language", ["English", "Hindi", "Marathi"], key="candidate_language")
    st.markdown("</div>", unsafe_allow_html=True)

    st.markdown("<div class='section-card chat-shell'>", unsafe_allow_html=True)
    st.markdown("<div class='section-title'>Candidate Chat</div>", unsafe_allow_html=True)

    for msg in st.session_state.messages:
        with st.chat_message(msg["role"]):
            render_message(msg["content"])

    prompt = st.chat_input("Ask about programs, eligibility, or how to register.")
    if prompt:
        st.session_state.messages.append({"role": "user", "content": prompt})
        with st.chat_message("user"):
            st.markdown(prompt)
        with st.chat_message("assistant"):
            with st.spinner("Thinking..."):
                sync_candidate_profile()
                response = call_backend(prompt)
                if response is None:
                    st.error(
                        "Backend not reachable. Start the API with `mvn spring-boot:run` and try again, "
                        "or set API_URL in your environment to the correct backend URL."
                    )
                elif response.status_code == 200:
                    advice = response.text
                    st.session_state.messages.append({"role": "assistant", "content": advice})
                    render_message(advice)
                else:
                    st.error(f"Server Error ({response.status_code}): {response.text}")

    st.markdown("</div>", unsafe_allow_html=True)
except Exception as exc:
    st.error("The chat UI hit an error and could not render. See details below.")
    st.exception(exc)
