from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    app_name: str = "CyberRangers"
    backend_url: str = os.getenv("BACKEND_URL", "http://localhost:8080")


settings = Settings()
