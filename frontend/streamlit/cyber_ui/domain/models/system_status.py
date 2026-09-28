from dataclasses import dataclass


@dataclass(frozen=True)
class SystemStatus:
    available: bool
    message: str
