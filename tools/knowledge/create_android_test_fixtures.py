"""Generate protocol interoperability fixtures with a disposable test key."""
import hashlib
import json
from pathlib import Path
import zipfile
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

root = Path(__file__).resolve().parents[2]
payload = json.loads((root / "app/src/main/assets/knowledge/builtin.json").read_text(encoding="utf-8"))
destination = root / "app/src/androidTest/assets/knowledge"
destination.mkdir(parents=True, exist_ok=True)
key = Ed25519PrivateKey.generate()
(destination / "test-publisher.key").write_bytes(key.public_key().public_bytes(
    serialization.Encoding.Raw, serialization.PublicFormat.Raw))
for version, count in ((1, 2), (2, 1)):
    body = json.dumps(dict(articles=payload["articles"][:count], sources=payload["sources"]),
                      ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
    manifest = json.dumps(dict(schema=1, version=version, count=count,
                               articles_sha256=hashlib.sha256(body).hexdigest()),
                          sort_keys=True, separators=(",", ":")).encode()
    with zipfile.ZipFile(destination / f"edition-{version}.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", manifest)
        archive.writestr("articles.json", body)
        archive.writestr("signature.ed25519", key.sign(manifest))
print("Created two Android interoperability fixtures; private test key discarded.")
