import os
import streamlit as st
import requests

st.set_page_config(page_title="Onboarding Velocity Engine", page_icon="?", layout="centered")

st.markdown(
    """
<style>
@import url('https://fonts.googleapis.com/css2?family=Fraunces:wght@600;700&family=Work+Sans:wght@300;400;500;600&display=swap');

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

.page-shell {
  background: radial-gradient(1000px 420px at 10% 10%, rgba(244, 123, 32, 0.18), transparent),
              radial-gradient(900px 420px at 90% 5%, rgba(15, 118, 110, 0.18), transparent),
              linear-gradient(180deg, #ffffff 0%, #f9fafb 100%);
  border: 1px solid #eef2f6;
  border-radius: 24px;
  padding: 30px 32px;
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.hero-title {
  font-family: 'Fraunces', serif;
  font-size: 2.1rem;
  line-height: 1.1;
  margin-bottom: 8px;
}

.hero-kicker {
  color: var(--mb-teal);
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-size: 0.78rem;
}

.section-card {
  background: #ffffff;
  border: 1px solid #eef2f6;
  border-radius: 18px;
  padding: 18px;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.section-title {
  font-family: 'Fraunces', serif;
  font-size: 1.4rem;
  margin-bottom: 10px;
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

.caption {
  color: #5b6472;
  font-size: 0.95rem;
}
</style>
""",
    unsafe_allow_html=True,
)

API_URL = os.getenv("API_URL", "http://localhost:8080/api/advisor/advice")
MAX_MB = 10

st.markdown("<div class='page-shell'>", unsafe_allow_html=True)
st.markdown("<div class='hero-kicker'>Magic Bus India Foundation</div>", unsafe_allow_html=True)
st.markdown("<div class='hero-title'>Onboarding Velocity Engine</div>", unsafe_allow_html=True)
st.markdown(
    "<div class='caption'>Upload documents ? AI extraction ? verification ? onboarded</div>",
    unsafe_allow_html=True,
)
st.markdown("</div>", unsafe_allow_html=True)

st.markdown("\n")

st.markdown("<div class='section-card'>", unsafe_allow_html=True)
st.markdown("<div class='section-title'>Tell us about you</div>", unsafe_allow_html=True)

candidate_name = st.text_input("Name")
candidate_age = st.selectbox("Age range", ["14-17", "18-24", "25-30", "31+"])
candidate_education = st.selectbox(
    "Education level",
    ["", "10th Pass", "12th Pass", "Graduate", "Undergraduate (In Progress)", "Diploma / ITI"],
)
candidate_location = st.text_input("Location (City/State)")
candidate_interests = st.multiselect(
    "Top interests",
    ["Technology", "Gaming", "Creative Arts", "Social Media", "Entrepreneurship",
     "Helping Others", "Music", "Movies", "Sports", "Writing", "Reading"],
)
candidate_skills = st.multiselect(
    "Current skills",
    ["Communication", "Basic Computer Literacy", "Microsoft Office", "Typing", "Teamwork",
     "Problem Solving", "Customer Service", "Sales", "Data Entry", "Python (Basics)",
     "Java (Basics)", "SQL (Basics)", "HTML/CSS (Basics)", "Video Editing (Beginner)",
     "Social Media Management", "Marketing", "Graphic Design (Beginner)"],
)
candidate_language = st.radio("Preferred language", ["English", "Hindi", "Marathi"], horizontal=True)

st.markdown("</div>", unsafe_allow_html=True)

st.markdown("\n")

st.markdown("<div class='section-card'>", unsafe_allow_html=True)
st.markdown("<div class='section-title'>Document uploads</div>", unsafe_allow_html=True)

aadhaar = st.file_uploader("Upload Aadhaar", type=["png", "jpg", "jpeg", "pdf"])
income = st.file_uploader("Upload Income Statement", type=["png", "jpg", "jpeg", "pdf"])

progress = st.progress(0)
status = st.empty()

if st.button("Start Verification", type="primary"):
    if any(f is not None and f.size > MAX_MB * 1024 * 1024 for f in [aadhaar, income]):
        st.error(f"File too large. Please upload files under {MAX_MB} MB.")
        st.stop()
    if not (aadhaar and income):
        st.warning("Please upload Aadhaar and Income documents.")
    else:
        status.write("Step 1: Upload")
        progress.progress(25)
        status.write("Step 2: AI Extracting...")
        progress.progress(50)

        files = {
            "aadhaar": aadhaar,
            "income": income,
        }
        data = {
            "candidateName": candidate_name,
            "candidateAgeRange": candidate_age,
            "candidateEducation": candidate_education,
            "candidateLocation": candidate_location,
            "candidateInterests": ", ".join(candidate_interests),
            "candidateSkills": ", ".join(candidate_skills),
            "candidateLanguage": candidate_language,
        }
        response = requests.post(
            API_URL.replace("/api/advisor/advice", "/api/onboarding/verify"),
            files=files,
            data=data,
            timeout=90,
        )

        status.write("Step 3: Verifying...")
        progress.progress(75)

        if response.status_code == 200:
            result = response.json()
            status.write("Step 4: Onboarded" if result.get("verified") else "Needs Mentor Review")
            progress.progress(100)
            if result.get("blobPath"):
                st.info(f"Saved to Blob: {result['blobPath']}")
            elif result.get("blobUploadError"):
                st.warning(f"Blob upload failed: {result['blobUploadError']}")
            st.json(result)
        else:
            st.error(f"Server error: {response.status_code}")
            st.text(response.text)

st.markdown("</div>", unsafe_allow_html=True)
