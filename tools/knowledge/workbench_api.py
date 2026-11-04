"""Loopback-only workbench endpoints. Content and signing stay in workflow.py."""
import hashlib
import json
import mimetypes
from pathlib import Path
import re
import secrets
import shutil
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlsplit
import urllib.error
import urllib.request

UPLOAD_LIMIT = 80 * 1024 * 1024


def create_server(flow, port):
    token = secrets.token_urlsafe(32)
    lock = threading.RLock()
    generation = dict(state="idle")

    def source_record(source_id):
        flow.ident(source_id)
        record = next((s for s in flow.sources() if s["id"] == source_id), None)
        if not record:
            raise ValueError("来源不存在，请刷新资料列表")
        path = (flow.WORK / record["file"]).resolve()
        if not path.is_relative_to(flow.WORK.resolve()) or not path.is_file():
            raise ValueError("原始资料找不到了，请重新导入")
        return record, path

    def guard_revision(article_id, expected):
        path = flow.WORK / "drafts" / (flow.ident(article_id) + ".json")
        if path.exists():
            revision = hashlib.sha256(flow.encoded(flow.read(path))).hexdigest()
            if expected != revision:
                raise ValueError("草稿已被其他 agent 修改，请先刷新并重新选择，再合并你的修改")
        elif expected:
            raise ValueError("草稿已移走，请刷新列表")
        return path

    def models():
        request = urllib.request.Request("http://127.0.0.1:11434/api/tags")
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open(request, timeout=3) as response:
                data = json.loads(response.read(1_000_000))
            return dict(available=True, models=[m["name"] for m in data.get("models", [])],
                        message="请选择电脑上已安装的模型")
        except (OSError, ValueError, KeyError):
            return dict(available=False, models=[],
                        message="未连接本机 Ollama。可以复制任务给当前 agent，或启动 Ollama 后重新检测。")

    def generate(data):
        source_id = flow.ident(data["source_id"])
        model = data.get("model", "").strip()
        if not model or len(model) > 100:
            raise ValueError("请先检测并选择已安装的本地模型")
        source_record(source_id)
        notes = flow.WORK / "sources" / source_id / "notes.txt"
        if not notes.exists() or not notes.read_text(encoding="utf-8").strip():
            raise ValueError("请先填写并保存已核对的知识笔记")
        with lock:
            if generation["state"] == "running":
                raise ValueError("已有生成任务正在运行，请等待完成")
            generation.clear()
            generation.update(state="running", message="本地模型正在撰写，生成后仍需核验和审核")

        def worker():
            try:
                result = flow.local_draft(source_id, model)
                with lock:
                    generation.update(state="done", result=result, message="草稿已生成，请核验引用与正文")
            except Exception as error:
                with lock:
                    generation.update(state="failed", message="生成未完成：" + str(error))
        threading.Thread(target=worker, daemon=True).start()
        return dict(generation)

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_):
            pass

        def local_host(self):
            return self.headers.get("Host") == f"127.0.0.1:{self.server.server_port}"

        def reply(self, code, value, mime="application/json"):
            body = value if isinstance(value, bytes) else flow.encoded(value)
            self.send_response(code)
            self.send_header("Content-Type", mime + "; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def send_file(self, path, download=False):
            mime = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
            self.send_response(200)
            self.send_header("Content-Type", mime)
            self.send_header("Content-Length", str(path.stat().st_size))
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("Cache-Control", "no-store")
            if download:
                self.send_header("Content-Disposition", f'attachment; filename="{path.name}"')
            self.end_headers()
            try:
                with path.open("rb") as stream:
                    shutil.copyfileobj(stream, self.wfile)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def do_GET(self):
            if not self.local_host():
                self.reply(403, dict(error="请使用终端显示的本机地址"))
                return
            url = urlsplit(self.path)
            if url.path == "/":
                html = Path(__file__).with_name("workbench.html").read_text(encoding="utf-8")
                self.reply(200, html.replace("__TOKEN__", token).encode(), "text/html")
                return
            if parse_qs(url.query).get("token") != [token]:
                self.reply(403, dict(error="请从工作台打开文件"))
                return
            try:
                if url.path.startswith("/source/"):
                    _, path = source_record(unquote(url.path[len("/source/"):]))
                    self.send_file(path)
                elif url.path.startswith("/download/"):
                    name = unquote(url.path[len("/download/"):])
                    if not re.fullmatch(r"stillune-knowledge-[0-9]+\.zip", name):
                        raise ValueError("知识包文件名不正确")
                    path = flow.ROOT / "output/knowledge" / name
                    if not path.is_file():
                        raise ValueError("知识包不存在")
                    self.send_file(path, download=True)
                elif url.path == "/guide":
                    self.send_file(flow.ROOT / "docs/knowledge-workflow/使用说明.md")
                else:
                    self.reply(404, dict(error="找不到页面"))
            except (ValueError, OSError) as error:
                self.reply(400, dict(error=str(error)))

        def upload(self):
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= UPLOAD_LIMIT:
                raise ValueError("请选择小于 80 MB 的资料")
            name = parse_qs(urlsplit(self.path).query).get("name", [""])[0]
            suffix = Path(name).suffix.lower()
            if suffix not in {".pdf", ".txt", ".md", ".png", ".jpg", ".jpeg"}:
                raise ValueError("请选择 PDF、TXT、Markdown 或图片")
            folder = flow.WORK / "uploads"
            folder.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(dir=folder) as temp:
                path = Path(temp) / ("upload" + suffix)
                remaining = length
                with path.open("wb") as stream:
                    while remaining:
                        block = self.rfile.read(min(remaining, 65536))
                        if not block:
                            raise ValueError("上传中断，请重新选择文件")
                        stream.write(block)
                        remaining -= len(block)
                return flow.ingest(path, Path(name).stem[:300] or "参考资料")

        def do_POST(self):
            if not self.local_host() or self.headers.get("X-Workbench-Token") != token:
                self.reply(403, dict(error="请从本机工作台操作"))
                return
            try:
                route = urlsplit(self.path).path
                if route == "/upload":
                    with lock:
                        result = self.upload()
                else:
                    length = int(self.headers.get("Content-Length", "0"))
                    if not 0 < length <= flow.MAX_BYTES:
                        raise ValueError("请求大小不符合要求")
                    data = json.loads(self.rfile.read(length))
                    if route == "/models":
                        result = models()
                    elif route == "/generate":
                        result = generate(data)
                    else:
                        with lock:
                            result = self.action(route, data)
                self.reply(200, result)
            except Exception as error:
                self.reply(400, dict(error=str(error) or type(error).__name__))

        def action(self, route, data):
            if route == "/status":
                return dict(flow.status(), generation=dict(generation))
            if route == "/ingest":
                return flow.ingest(data["path"], data.get("title") or None)
            if route == "/save":
                article = data["article"]
                guard_revision(article["id"], data.get("revision"))
                return flow.save(article)
            if route == "/approve":
                guard_revision(data["id"], data.get("revision"))
                return flow.approve(data["id"], data["reviewer"], data["note"])
            if route == "/draft-new":
                article = flow.template(data["source_id"], "draft-" + secrets.token_hex(4))
                flow.save(article)
                return dict(id=article["id"])
            if route == "/draft-import":
                payload = data["payload"]
                rows = payload.get("articles", [payload]) if isinstance(payload, dict) else payload
                if not isinstance(rows, list) or not 1 <= len(rows) <= 200:
                    raise ValueError("请选择单篇草稿或包含 articles 的 JSON")
                ids = set()
                for row in rows:
                    row = flow.normalize_draft(row)
                    article_id = flow.ident(row.get("id"))
                    if article_id in ids or (flow.WORK / "drafts" / (article_id + ".json")).exists():
                        raise ValueError("草稿 ID 重复，导入前请为新稿更换 ID：" + article_id)
                    ids.add(article_id)
                for row in rows:
                    flow.save(row)
                return dict(count=len(rows), id=rows[0]["id"])
            if route == "/source-info":
                record, path = source_record(data["id"])
                notes = flow.WORK / "sources" / record["id"] / "notes.txt"
                return dict(source=record, notes=notes.read_text(encoding="utf-8") if notes.exists() else "",
                            file_type=path.suffix.lower())
            if route == "/notes":
                record, _ = source_record(data["id"])
                text = data["text"]
                if not isinstance(text, str) or len(text) > 16_000:
                    raise ValueError("每次知识笔记限 16000 字符")
                path = flow.WORK / "sources" / record["id"] / "notes.txt"
                path.write_text(text, encoding="utf-8")
                return dict(message="知识笔记已保存")
            if route == "/extract":
                record, path = source_record(data["id"])
                if path.suffix.lower() in {".txt", ".md"}:
                    return dict(text=path.read_text(encoding="utf-8")[:16_000], message="已读取文字，请核对后保存为笔记")
                if path.suffix.lower() != ".pdf":
                    raise ValueError("图片请先交给 agent 识别，或在旁边填写知识笔记")
                try:
                    from pypdf import PdfReader
                except ImportError as error:
                    raise ValueError("缺少 PDF 文字提取依赖，请按使用说明安装 pypdf") from error
                start, end = data.get("start"), data.get("end")
                if type(start) is not int or type(end) is not int or not 1 <= start <= end <= start + 9:
                    raise ValueError("每次选择 1–10 页，PDF 页序从 1 开始")
                reader = PdfReader(path)
                if end > len(reader.pages):
                    raise ValueError(f"这份 PDF 只有 {len(reader.pages)} 页")
                text = "\n\n".join(f"PDF 第 {i+1} 页\n{reader.pages[i].extract_text() or ''}" for i in range(start - 1, end))
                if not any((reader.pages[i].extract_text() or "").strip() for i in range(start - 1, end)):
                    raise ValueError("这些页没有可提取的文字层。请打开原件核对，交给 agent 做图片识别后再保存笔记。")
                if len(text) > 16_000:
                    raise ValueError("文字超过笔记长度，请减少本次页数")
                return dict(text=text, message="已提取文字，请对照原页核验")
            if route == "/task":
                record, _ = source_record(data["id"])
                path = flow.WORK / "tasks" / (record["id"] + ".md")
                text = path.read_text(encoding="utf-8") if path.exists() else ""
                return dict(text=text + "\n请读取项目技能。保存草稿后由开发者在工作台审核，保留来源页码。")
            if route == "/archive":
                path = guard_revision(data["id"], data.get("revision"))
                target = flow.WORK / "archive" / (path.stem + "-" + secrets.token_hex(4) + ".json")
                target.parent.mkdir(parents=True, exist_ok=True)
                path.replace(target)
                return dict(message="草稿已归档，可从归档列表恢复")
            if route == "/restore":
                name = data["file"]
                if not isinstance(name, str) or Path(name).name != name or not re.fullmatch(r"[a-z0-9-]+\.json", name):
                    raise ValueError("归档文件名不正确")
                path = flow.WORK / "archive" / name
                article_id = flow.ident(flow.read(path)["id"])
                target = flow.WORK / "drafts" / (article_id + ".json")
                if target.exists():
                    raise ValueError("相同 ID 的草稿仍在列表中，请先处理该草稿")
                path.replace(target)
                return dict(id=article_id)
            if route == "/keygen":
                return flow.keygen()
            if route == "/package":
                diagnostics = flow.status()["signing"]
                if not diagnostics["ready"]:
                    raise ValueError(diagnostics["message"])
                return flow.package(data["version"])
            raise ValueError("未知操作，请刷新工作台")

    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    server.daemon_threads = True
    return server
