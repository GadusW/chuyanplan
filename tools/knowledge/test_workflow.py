"""Regression checks for approval binding and package authenticity."""
import copy
import io
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("knowledge_workflow", Path(__file__).with_name("workflow.py"))
flow = importlib.util.module_from_spec(spec)
spec.loader.exec_module(flow)


class KnowledgeWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.old_root, self.old_work = flow.ROOT, flow.WORK
        self.addCleanup(self.restore)
        flow.ROOT = Path(self.temp.name)
        flow.WORK = flow.ROOT / ".knowledge-workspace"
        self.input = flow.ROOT / "资料.txt"
        self.input.write_text("这是开发测试参考资料。", encoding="utf-8")
        self.source = flow.ingest(self.input, "测试来源")
        self.article = flow.template(self.source["id"], "one-question")
        # A length fixture exercises validation; this is never shipped as reading content.
        self.article.update(title="测试文章", paragraphs=["声学知识" * 55] * 3, takeaway="测试结尾")
        self.article["citations"] = [dict(source_id=self.source["id"], pdf_pages=[1],
                                         printed_pages="1", verified=True)]
        flow.save(self.article)

    def restore(self):
        flow.ROOT, flow.WORK = self.old_root, self.old_work

    def approve(self):
        flow.approve(self.article["id"], "测试审核人", "测试批准当前版本")

    def test_duplicate_import_and_agent_handoff(self):
        self.assertEqual(self.source["id"], flow.ingest(self.input)["id"])
        self.assertEqual(1, len(flow.status()["sources"]))
        task = flow.WORK / "tasks" / (self.source["id"] + ".md")
        self.assertIn("skills/stillune-knowledge/SKILL.md", task.read_text(encoding="utf-8"))
        self.assertEqual([], flow.check(self.article))
        self.assertIn("篇数不设", task.read_text(encoding="utf-8"))

    def test_longer_reading_and_epub_spine_are_supported(self):
        article = copy.deepcopy(self.article)
        article["paragraphs"] = ["声学知识" * 90] * 3
        self.assertEqual([], flow.check(article))
        epub = flow.ROOT / "test.epub"
        with zipfile.ZipFile(epub, "w") as archive:
            archive.writestr("META-INF/container.xml", '<container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>')
            archive.writestr("OPS/book.opf", '<package><manifest><item id="b" href="b.xhtml"/><item id="a" href="a.xhtml"/></manifest><spine><itemref idref="a"/><itemref idref="b"/></spine></package>')
            archive.writestr("OPS/a.xhtml", '<html><body><p>第一节</p></body></html>')
            archive.writestr("OPS/b.xhtml", '<html><body><p>第二节</p></body></html>')
        source = flow.ingest(epub, "EPUB 测试")
        sections = flow.epub_sections(flow.WORK / source["file"])
        self.assertEqual(["第一节", "第二节"], [s["text"] for s in sections])

    def test_unverified_and_unapproved_drafts_cannot_be_published(self):
        self.article["citations"][0]["verified"] = False
        flow.save(self.article)
        with self.assertRaises(ValueError):
            self.approve()
        with self.assertRaises(ValueError):
            flow.package(1)

    def test_editing_invalidates_approval(self):
        self.approve()
        self.assertTrue(flow.approved(self.article))
        changed = copy.deepcopy(self.article)
        changed["takeaway"] = "改过的结尾"
        flow.save(changed)
        self.assertFalse(flow.approved(changed))
        self.assertTrue(flow.status()["articles"][0]["approved"] is False)

    def test_changed_source_blocks_approval(self):
        source_path = flow.WORK / self.source["file"]
        source_path.write_text("参考资料后来发生了修改", encoding="utf-8")
        with self.assertRaises(ValueError):
            self.approve()

    def test_bad_draft_does_not_break_status_or_overwrite_valid_article(self):
        (flow.WORK / "drafts/broken.json").write_text("{", encoding="utf-8")
        flow.write(flow.WORK / "drafts/wrong-type.json", dict(id="wrong-type", title=123))
        state = flow.status()
        self.assertEqual(1, len(state["articles"]))
        self.assertEqual(2, len(state["issues"]))
        with self.assertRaises(ValueError):
            flow.save(dict(id=self.article["id"], title=123))
        self.assertEqual("测试文章", flow.read(flow.WORK / "drafts/one-question.json")["title"])
        flow.save(dict(id="unfinished-draft", title="未完成"))
        self.assertTrue(flow.status()["articles"])

    def test_model_output_cannot_overwrite_existing_article_or_self_verify(self):
        notes = flow.WORK / "sources" / self.source["id"] / "notes.txt"
        notes.write_text("经过核对的测试知识点", encoding="utf-8")
        guide = flow.ROOT / "docs/knowledge-workflow/内容规范.md"
        guide.parent.mkdir(parents=True, exist_ok=True)
        guide.write_text("测试写作规范", encoding="utf-8")
        response = json.dumps(dict(response=json.dumps(self.article, ensure_ascii=False))).encode()
        class FakeOpener:
            def open(self, *args, **kwargs):
                return io.BytesIO(response)
        with patch.object(flow.urllib.request, "build_opener", return_value=FakeOpener()):
            result = flow.local_draft(self.source["id"], "test-model")
        self.assertNotEqual(self.article["id"], result["id"])
        created = flow.read(flow.WORK / "drafts" / (result["id"] + ".json"))
        self.assertFalse(created["citations"][0]["verified"])
        self.assertEqual(self.article, flow.read(flow.WORK / "drafts/one-question.json"))

    def test_sign_verify_tampering_wrong_key_and_immutable_version(self):
        self.approve()
        flow.keygen()
        with self.assertRaises(ValueError):
            flow.keygen()
        result = flow.package(1)
        package = Path(result["path"])
        public = flow.WORK / "keys/public.pem"
        self.assertEqual(1, flow.verify(package, public)["count"])
        with self.assertRaises(ValueError):
            flow.package(1)
        with zipfile.ZipFile(package) as archive:
            entries = {name: archive.read(name) for name in archive.namelist()}
        entries["articles.json"] = entries["articles.json"].replace("测试文章".encode(), "篡改文章".encode())
        tampered = flow.ROOT / "tampered.zip"
        with zipfile.ZipFile(tampered, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        with self.assertRaises(ValueError):
            flow.verify(tampered, public)
        serialization, key_type = flow.crypto()
        wrong = flow.ROOT / "wrong.pem"
        wrong.write_bytes(key_type.generate().public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo))
        with self.assertRaises(Exception):
            flow.verify(package, wrong)
        with zipfile.ZipFile(package) as archive:
            self.assertEqual({"manifest.json", "articles.json", "signature.ed25519"}, set(archive.namelist()))
            self.assertNotIn(str(flow.WORK).encode(), archive.read("articles.json"))


if __name__ == "__main__":
    unittest.main()
