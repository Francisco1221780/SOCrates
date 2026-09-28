import streamlit as st

from cyber_ui.application.use_cases.check_backend_status import CheckBackendStatus
from cyber_ui.config.settings import settings
from cyber_ui.infrastructure.backend_client import BackendClient
from cyber_ui.presentation.components.status_card import render_status_card


def render_home_page() -> None:
    st.title("🛡️ CyberRangers")
    st.caption("Painel de monitorização de segurança")

    with st.sidebar:
        st.header("Configuração")
        backend_url = st.text_input("URL do backend", value=settings.backend_url)

    st.subheader("Visão geral")
    status = CheckBackendStatus(BackendClient(backend_url)).execute()
    render_status_card(status)

    st.divider()
    first, second, third = st.columns(3)
    first.metric("Incidentes", "0", "Nenhum novo")
    second.metric("Alertas", "0", "Nenhum novo")
    third.metric("Estado", "Seguro" if status.available else "Offline")

    if st.button("Verificar novamente", type="primary"):
        st.rerun()
