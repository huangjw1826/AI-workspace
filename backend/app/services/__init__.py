"""Service layer."""

from .remote_access import remote_access_manager, RemoteAccessManager, RemoteAccessStatus

__all__ = [
    "remote_access_manager",
    "RemoteAccessManager",
    "RemoteAccessStatus",
]

