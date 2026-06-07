import asyncio
import logging
import os
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Tuple

from app.config import get_settings

logger = logging.getLogger(__name__)


@dataclass
class RemoteAccessStatus:
    enabled: bool
    running: bool
    provider: str
    hostname: str
    tunnel_id: str
    error: Optional[str] = None
    connection_info: Optional[dict] = None


class RemoteAccessManager:
    def __init__(self):
        self.settings = get_settings()
        self._process: Optional[subprocess.Popen] = None
        self._running = False
        self._start_time = None
        self._connection_info = None
        self._error = None

    @property
    def status(self) -> RemoteAccessStatus:
        return RemoteAccessStatus(
            enabled=self.settings.remote_access_enabled,
            running=self._running,
            provider=self.settings.remote_access_provider,
            hostname=self.settings.remote_access_hostname,
            tunnel_id=self.settings.remote_access_tunnel_id,
            error=self._error,
            connection_info=self._connection_info,
        )

    async def health_check(self) -> Tuple[bool, str]:
        """检查远程访问健康状态"""
        if not self.settings.remote_access_enabled:
            return True, "Remote access is disabled"
        
        if not self._running:
            return False, "Remote access service is not running"
        
        if self._error:
            return False, f"Error: {self._error}"
        
        return True, "Remote access is running"

    async def start(self) -> bool:
        """启动远程访问服务"""
        if not self.settings.remote_access_enabled:
            logger.info("Remote access is disabled in settings")
            return False

        if self._running:
            logger.info("Remote access is already running")
            return True

        try:
            await self._preflight_check()
            
            provider = self.settings.remote_access_provider.lower()
            if provider == "cloudflare":
                success = await self._start_cloudflare_tunnel()
            else:
                logger.error(f"Unsupported remote access provider: {provider}")
                self._error = f"Unsupported provider: {provider}"
                return False

            if success:
                self._running = True
                self._start_time = time.time()
                self._error = None
                logger.info("Remote access service started successfully")
                return True
            else:
                self._error = "Failed to start remote access service"
                return False

        except Exception as e:
            logger.error(f"Failed to start remote access: {e}", exc_info=True)
            self._error = str(e)
            return False

    async def stop(self) -> bool:
        """停止远程访问服务"""
        if not self._running:
            logger.info("Remote access is not running")
            return True

        try:
            if self._process and self._process.poll() is None:
                logger.info("Stopping remote access process...")
                self._process.terminate()
                
                try:
                    await asyncio.wait_for(
                        asyncio.get_event_loop().run_in_executor(
                            None, self._process.wait, 5
                        ),
                        timeout=10.0,
                    )
                except asyncio.TimeoutError:
                    logger.warning("Force killing remote access process")
                    self._process.kill()

            self._process = None
            self._running = False
            self._start_time = None
            self._connection_info = None
            logger.info("Remote access service stopped successfully")
            return True

        except Exception as e:
            logger.error(f"Failed to stop remote access: {e}", exc_info=True)
            return False

    async def _preflight_check(self) -> None:
        """启动前自检（cloudflared 和配置为硬阻断，后端连通性为软检查）"""
        hard_checks = [
            ("Cloudflared binary", self._check_cloudflared_binary),
            ("Tunnel configuration", self._check_tunnel_config),
        ]
        for check_name, check_func in hard_checks:
            try:
                await check_func()
                logger.info("[OK] %s", check_name)
            except Exception as e:
                logger.error("[FAIL] %s: %s", check_name, e)
                raise

        # 后端连通性仅做探测，不阻断启动（启动阶段服务器尚未监听端口）
        try:
            await self._check_backend_service()
        except Exception:
            pass  # 已在 _check_backend_service 内部记录警告

    async def _check_cloudflared_binary(self) -> None:
        """检查 cloudflared 二进制文件"""
        paths = [
            Path(".tools/cloudflared.exe"),
            Path("C:/Program Files (x86)/cloudflared/cloudflared.exe"),
            Path(os.environ.get("PROGRAMFILES", "C:/Program Files") + "/cloudflared/cloudflared.exe"),
        ]
        
        found = False
        for path in paths:
            if path.exists():
                found = True
                break
        
        if not found:
            raise FileNotFoundError(
                "cloudflared binary not found. Install via: winget install Cloudflare.cloudflared"
            )

    async def _check_tunnel_config(self) -> None:
        """检查隧道配置"""
        config_path = self._get_config_path()
        if not config_path.exists():
            raise FileNotFoundError(f"Cloudflare config not found: {config_path}")

        if not self.settings.remote_access_tunnel_name and not self.settings.remote_access_tunnel_id:
            raise ValueError("Either tunnel_name or tunnel_id must be configured")

    async def _check_backend_service(self) -> None:
        """检查后端服务是否就绪（单次探测，失败时警告不阻塞启动）

        Uvicorn 在 startup 事件返回后才开始监听端口，因此自动启动时此检查
        必定失败。这里只做 single-shot 探测，失败时记录警告但不影响隧道启动。
        """
        import httpx
        url = f"http://{self.settings.app_host}:{self.settings.app_port}/health"
        try:
            async with httpx.AsyncClient(timeout=3) as client:
                response = await client.get(url)
                if response.status_code == 200:
                    logger.info("Backend service is reachable")
                    return
                logger.warning("Backend health check returned %d", response.status_code)
        except Exception as e:
            logger.warning("Backend health check unavailable (may be starting up): %s", e)

    async def _start_cloudflare_tunnel(self) -> bool:
        """启动 Cloudflare Tunnel"""
        config_path = self._get_config_path()
        log_path = self._get_log_path()
        pid_path = self._get_pid_path()

        # --config 是 tunnel 级别的选项，必须在 run 子命令之前
        args = [
            str(self._get_cloudflared_path()),
            "tunnel",
            "--config", str(config_path),
            "run",
        ]

        if self.settings.remote_access_tunnel_name:
            args.append(self.settings.remote_access_tunnel_name)
        elif self.settings.remote_access_tunnel_id:
            args.append(self.settings.remote_access_tunnel_id)

        log_path.parent.mkdir(parents=True, exist_ok=True)
        
        with open(log_path, "w", encoding="utf-8") as log_file:
            self._process = subprocess.Popen(
                args,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                creationflags=subprocess.CREATE_NEW_PROCESS_GROUP,
            )

        with open(pid_path, "w", encoding="utf-8") as pid_file:
            pid_file.write(str(self._process.pid))

        await asyncio.sleep(3)
        
        if self._process.poll() is not None:
            with open(log_path, "r", encoding="utf-8") as f:
                logs = f.read()
            logger.error(f"Cloudflare tunnel failed to start:\n{logs}")
            return False

        await self._wait_for_tunnel_ready(log_path)
        return True

    async def _wait_for_tunnel_ready(self, log_path: Path, timeout: int = 30) -> None:
        """等待隧道连接就绪"""
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self._process and self._process.poll() is not None:
                raise RuntimeError("Tunnel process terminated")

            try:
                with open(log_path, "r", encoding="utf-8") as f:
                    content = f.read()
                    if "Registered tunnel connection" in content:
                        logger.info("Tunnel connection registered")
                        return
            except Exception:
                pass

            await asyncio.sleep(1)

        raise TimeoutError("Tunnel failed to connect within timeout")

    def _get_cloudflared_path(self) -> Path:
        """获取 cloudflared 二进制路径"""
        paths = [
            Path(".tools/cloudflared.exe"),
            Path("C:/Program Files (x86)/cloudflared/cloudflared.exe"),
        ]
        for path in paths:
            if path.exists():
                return path
        return Path("cloudflared.exe")

    def _get_config_path(self) -> Path:
        """获取配置文件路径"""
        if self.settings.remote_access_config_path:
            return Path(self.settings.remote_access_config_path)
        
        user_config = Path(os.path.expanduser("~/.cloudflared/config.yml"))
        if user_config.exists():
            return user_config
        
        return Path(".tools/config.yml")

    def _get_log_path(self) -> Path:
        """获取日志文件路径"""
        if self.settings.remote_access_log_path:
            return Path(self.settings.remote_access_log_path)
        return self.settings.resolved_log_dir / "cloudflared.log"

    def _get_pid_path(self) -> Path:
        """获取 PID 文件路径"""
        if self.settings.remote_access_pid_path:
            return Path(self.settings.remote_access_pid_path)
        return self.settings.resolved_log_dir / "cloudflared.pid"


remote_access_manager = RemoteAccessManager()