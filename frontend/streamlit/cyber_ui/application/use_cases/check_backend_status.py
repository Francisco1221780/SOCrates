from cyber_ui.domain.models.system_status import SystemStatus
from cyber_ui.infrastructure.backend_client import BackendClient


class CheckBackendStatus:
    def __init__(self, backend_client: BackendClient) -> None:
        self._backend_client = backend_client

    def execute(self) -> SystemStatus:
        return self._backend_client.check_status()
