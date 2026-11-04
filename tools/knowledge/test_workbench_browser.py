"""Run real Edge UI checks in an isolated workspace. Requires playwright."""
import importlib.util
import json
import re
from pathlib import Path
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request

from playwright.sync_api import sync_playwright, expect

sys.path.insert(0, str(Path(__file__).parent))
import workbench_api

spec = importlib.util.spec_from_file_location("workflow", Path(__file__).with_name("workflow.py"))
flow = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = flow
spec.loader.exec_module(flow)


def main():
    with tempfile.TemporaryDirectory() as temp:
        flow.ROOT = Path(temp)
        flow.WORK = flow.ROOT / ".knowledge-workspace"
        source_file = flow.ROOT / "参考笔记.txt"
        source_file.write_text("这是自动化测试的独立参考笔记。\n只在临时工作区使用。", encoding="utf-8")
        server = workbench_api.create_server(flow, 0)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        url = f"http://127.0.0.1:{server.server_port}"
        try:
            with sync_playwright() as browser_api:
                browser = browser_api.chromium.launch(
                    executable_path="C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                    headless=True)
                page = browser.new_page(viewport={"width": 1360, "height": 1000})
                failures = []
                page.on("pageerror", lambda error: failures.append(str(error)))
                page.on("dialog", lambda dialog: dialog.accept())
                page.goto(url)
                expect(page.locator("#message")).to_have_text("工作台已连接。")
                expect(page.locator("#package")).to_be_disabled()
                expect(page.locator("#step-source")).to_have_attribute("aria-current", "step")
                page.locator("#help").click()
                expect(page.locator("#help-dialog")).to_be_visible()
                page.locator("#close-help").click()
                page.locator("#upload").set_input_files(source_file)
                expect(page.locator("#sources")).to_contain_text("参考笔记")
                expect(page.locator("#step-draft")).to_have_attribute("aria-current", "step")
                page.get_by_role("button", name="整理资料", exact=True).click()
                expect(page.locator("#source-panel")).to_be_visible()
                source = flow.sources()[0]
                page.locator("#task").click()
                expect(page.locator("#task-dialog")).to_be_visible()
                expect(page.locator("#task-text")).to_have_value(re.compile("待处理资料"))
                page.locator("#close-task").click()
                original_url = page.locator("#original").get_attribute("href")
                assert urllib.request.urlopen(url + original_url).read() == source_file.read_bytes()
                page.get_by_text("自己整理笔记 / 使用本地模型（选用）", exact=True).click()
                page.locator("#extract").click()
                expect(page.locator("#notes")).to_have_value(source_file.read_text(encoding="utf-8"))
                page.locator("#save-notes").click()
                expect(page.locator("#message")).to_have_text("知识笔记已保存")
                page.locator("#new-draft").click()
                expect(page.locator("#article-panel")).to_be_visible()
                expect(page.locator("#step-review")).to_have_attribute("aria-current", "step")
                article_id = flow.status()["articles"][0]["article"]["id"]
                page.locator("#title").fill("浏览器流程验证")
                page.locator("#body").fill("\n\n".join(["声学知识" * 55] * 3))
                page.locator("#takeaway").fill("这段正文只用于测试。")
                page.locator(".ref-pages").fill("1")
                page.locator(".ref-printed").fill("无页码")
                page.locator(".ref-verified").check()
                page.locator("#refresh").click()
                expect(page.locator("#title")).to_have_value("浏览器流程验证")
                expect(page.locator("#message")).to_contain_text("未保存内容已保留")
                page.locator("#preview-button").click()
                expect(page.locator("#preview")).to_contain_text("浏览器流程验证")
                page.locator("#close-preview").click()
                page.locator("#save").click()
                expect(page.locator("#validation")).to_contain_text("格式检查通过")
                expect(page.locator("#approve")).to_be_disabled()
                page.locator("#reviewer").fill("临时测试审核人")
                page.locator("#note").fill("仅测试当前临时工作区的审核流程")
                page.locator("#confirm").check()
                page.locator("#approve").click()
                expect(page.locator("#validation")).to_contain_text("审核已通过")
                expect(page.locator("#step-deliver")).to_have_attribute("aria-current", "step")
                page.locator("#keygen").click()
                expect(page.locator("#keygen")).to_be_disabled()
                expect(page.locator("#signing")).to_contain_text("签名可用")
                page.locator("#package").click()
                expect(page.locator("#packs")).to_contain_text("stillune-knowledge-1.zip")
                expect(page.locator("#version")).to_have_value("2")
                with page.expect_download() as download_event:
                    page.get_by_role("link", name="下载知识包").click()
                downloaded = flow.ROOT / "downloaded.zip"
                download_event.value.save_as(downloaded)
                assert flow.verify(downloaded, flow.WORK / "keys/public.pem")["count"] == 1
                page.locator("#title").fill("尚未保存的标题")
                changed = flow.read(flow.WORK / "drafts" / (article_id + ".json"))
                changed["takeaway"] = "另外一个 agent 修改过的结尾"
                flow.save(changed)
                page.locator("#save").click()
                expect(page.locator("#message")).to_contain_text("其他 agent 修改")
                expect(page.locator("#title")).to_have_value("尚未保存的标题")
                page.locator("#refresh").click()
                page.locator("#articles").get_by_role("button", name="编辑", exact=True).click()
                expect(page.locator("#title")).to_have_value("浏览器流程验证")
                expect(page.locator("#validation")).to_contain_text("格式检查通过")
                page.locator("#archive").click()
                expect(page.locator("#archives")).to_contain_text("浏览器流程验证")
                page.get_by_text("已归档草稿", exact=True).click()
                page.locator("#archives").get_by_role("button", name="恢复").click()
                expect(page.locator("#article-panel")).to_be_visible()
                imported = flow.read(flow.WORK / "drafts" / (article_id + ".json"))
                imported["id"] = "imported-draft"
                imported["title"] = "从 agent 导入的草稿"
                imported["citations"][0]["verified"] = False
                json_file = flow.ROOT / "draft.json"
                json_file.write_text(json.dumps(imported, ensure_ascii=False), encoding="utf-8")
                page.locator("#draft-file").set_input_files(json_file)
                expect(page.locator("#title")).to_have_value("从 agent 导入的草稿")
                expect(page.locator("#validation")).to_contain_text("核对原页")
                assert flow.status()["articles"][0]["approved"] is False
                (flow.WORK / "drafts/broken.json").write_text("{", encoding="utf-8")
                page.locator("#refresh").click()
                expect(page.locator("#articles")).to_contain_text("从 agent 导入的草稿")
                assert flow.status()["issues"]

                # The queue is tested with a simulated model, without downloading one.
                def fake_model(source_id, model):
                    time.sleep(.3)
                    article = dict(imported, id="generated-draft", title="本地模型队列测试")
                    return flow.save(article)
                flow.local_draft = fake_model
                page.route("**/models", lambda route: route.fulfill(
                    json={"available": True, "models": ["test-model"], "message": "测试模型"}))
                page.get_by_role("button", name="整理资料", exact=True).click()
                page.locator("#models").click()
                page.locator("#model").select_option("test-model")
                page.locator("#generate").click()
                expect(page.locator("#title")).to_have_value("本地模型队列测试", timeout=10_000)
                assert not failures, failures
                browser.close()
                print("Browser workflow passed: upload, source/task, notes, create/edit/preview, dirty refresh, review,")
                print("sign/download/verify, concurrent edit guard, archive/restore, JSON import, damaged draft isolation, model queue.")
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    main()
