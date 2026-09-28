import requests

from cyber_ui.domain.models.system_status import SystemStatus


class BackendClient:
    def __init__(self, base_url: str, timeout: float = 3.0) -> None:
        self._base_url = base_url.rstrip("/")
        self._timeout = timeout

    def check_status(self) -> SystemStatus:
        try:
            response = requests.get(self._base_url, timeout=self._timeout)
            if response.ok:
                return SystemStatus(True, "Backend online")
            return SystemStatus(
                True,
                f"Backend acessível (HTTP {response.status_code})",
            )
        except requests.RequestException:
            return SystemStatus(False, "Backend indisponível")
