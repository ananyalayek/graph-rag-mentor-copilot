import streamlit as st
import requests

st.set_page_config(page_title="Onboarding Velocity Engine", layout="centered")

st.title("Onboarding Velocity Engine")
MAX_MB = 10

st.caption("Upload documents → AI extraction → verification → onboarded")

student_id = st.text_input("Student ID")

aadhaar = st.file_uploader("Upload Aadhaar", type=["png", "jpg", "jpeg", "pdf"])
pan = st.file_uploader("Upload PAN", type=["png", "jpg", "jpeg", "pdf"])
income = st.file_uploader("Upload Income Statement", type=["png", "jpg", "jpeg", "pdf"])

progress = st.progress(0)
status = st.empty()

if st.button("Start Verification", type="primary"):
    if any(f is not None and f.size > MAX_MB * 1024 * 1024 for f in [aadhaar, pan, income]):
        st.error(f"File too large. Please upload files under {MAX_MB} MB.")
        st.stop()
    if not (student_id and aadhaar and pan and income):
        st.warning("Please upload all documents and enter a student ID.")
    else:
        status.write("Step 1: Upload ✅")
        progress.progress(25)
        status.write("Step 2: AI Extracting…")
        progress.progress(50)

        files = {
            "aadhaar": aadhaar,
            "pan": pan,
            "income": income,
        }
        data = {"studentId": student_id}
        response = requests.post("http://localhost:8080/api/onboarding/verify", files=files, data=data, timeout=90)

        status.write("Step 3: Verifying…")
        progress.progress(75)

        if response.status_code == 200:
            result = response.json()
            status.write("Step 4: Onboarded ✅" if result.get("verified") else "Needs Mentor Review")
            progress.progress(100)
            st.json(result)
        else:
            st.error(f"Server error: {response.status_code}")
            st.text(response.text)
