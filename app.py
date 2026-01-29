# Backward-compatible entrypoint for older commands.
try:
    from Chat import *  # noqa: F401,F403
except Exception as exc:
    import streamlit as st
    st.set_page_config(page_title="Chat", layout="wide")
    st.error(f"Failed to load Chat UI: {exc}")
    st.info("Run: streamlit run Chat.py")