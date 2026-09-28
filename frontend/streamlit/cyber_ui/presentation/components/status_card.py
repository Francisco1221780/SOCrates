import streamlit as st

from cyber_ui.domain.models.system_status import SystemStatus


def render_status_card(status: SystemStatus) -> None:
    color = "#16a34a" if status.available else "#dc2626"
    st.markdown(
        f"""
        <div style="border-left: 5px solid {color}; padding: 1rem;
                    background: white; border-radius: 0.5rem;">
            <strong style="color: {color};">{status.message}</strong>
        </div>
        """,
        unsafe_allow_html=True,
    )
