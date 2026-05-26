import unittest

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api.auth import LocalBypassTokenMiddleware
from app.api.filesystem import router as filesystem_router


class LocalBypassTokenMiddlewareTest(unittest.TestCase):
    def build_client(self, token: str, base_url: str = "https://recorder.example.com") -> TestClient:
        app = FastAPI()
        app.add_middleware(LocalBypassTokenMiddleware, api_token=token)

        @app.get("/health")
        def health() -> dict[str, str]:
            return {"status": "ok"}

        @app.get("/api/recordings")
        def recordings() -> list[dict[str, str]]:
            return [{"id": "rec-1"}]

        return TestClient(app, base_url=base_url)

    def test_health_remains_public_when_token_is_configured(self) -> None:
        client = self.build_client("secret-token")

        response = client.get("/health")

        self.assertEqual(response.status_code, 200)

    def test_remote_api_routes_require_token_when_configured(self) -> None:
        client = self.build_client("secret-token")

        response = client.get("/api/recordings")

        self.assertEqual(response.status_code, 403)

    def test_remote_api_routes_accept_matching_token(self) -> None:
        client = self.build_client("secret-token")

        response = client.get("/api/recordings", headers={"X-API-Token": "secret-token"})

        self.assertEqual(response.status_code, 200)

    def test_remote_api_routes_reject_wrong_token(self) -> None:
        client = self.build_client("secret-token")

        response = client.get("/api/recordings", headers={"X-API-Token": "wrong-token"})

        self.assertEqual(response.status_code, 403)

    def test_loopback_host_bypasses_token_for_pc_browser(self) -> None:
        client = self.build_client("secret-token", base_url="http://127.0.0.1:8000")

        response = client.get("/api/recordings")

        self.assertEqual(response.status_code, 200)

    def test_loopback_origin_requires_loopback_host(self) -> None:
        client = self.build_client("secret-token", base_url="https://recorder.example.com")

        response = client.get("/api/recordings", headers={"Origin": "http://127.0.0.1:8000"})

        self.assertEqual(response.status_code, 403)

    def test_loopback_origin_and_host_bypass_token(self) -> None:
        client = self.build_client("secret-token", base_url="http://127.0.0.1:8000")

        response = client.get("/api/recordings", headers={"Origin": "http://127.0.0.1:8000"})

        self.assertEqual(response.status_code, 200)

    def test_ipv6_loopback_host_bypasses_token(self) -> None:
        client = self.build_client("secret-token")

        response = client.get("/api/recordings", headers={"Host": "[::1]:8000"})

        self.assertEqual(response.status_code, 200)

    def test_api_routes_stay_open_when_token_is_not_configured(self) -> None:
        client = self.build_client("")

        response = client.get("/api/recordings")

        self.assertEqual(response.status_code, 200)


class FolderPickerAccessTest(unittest.TestCase):
    def test_folder_picker_rejects_remote_requests_even_with_token(self) -> None:
        app = FastAPI()
        app.add_middleware(LocalBypassTokenMiddleware, api_token="secret-token")
        app.include_router(filesystem_router)
        client = TestClient(app, base_url="https://recorder.example.com")

        response = client.post("/api/pick-folder", headers={"X-API-Token": "secret-token"})

        self.assertEqual(response.status_code, 403)


if __name__ == "__main__":
    unittest.main()
