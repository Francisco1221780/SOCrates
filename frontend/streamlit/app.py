import streamlit as st

from cyber_ui.config.settings import settings
from cyber_ui.presentation.pages.home import render_home_page


st.set_page_config(
    page_title=settings.app_name,
    page_icon="🛡️",
    layout="wide",
    initial_sidebar_state="expanded",
)

render_home_page()
