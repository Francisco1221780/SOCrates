from cyber_ui.infrastructure.backend_client import BackendClient


def test_backend_client_reports_unavailable_backend() -> None:
    status = BackendClient("http://127.0.0.1:1", timeout=0.1).check_status()

    assert status.available is False
