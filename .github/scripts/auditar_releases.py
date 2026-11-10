"""Auditoría de APK publicados, ejecutada sólo en GitHub Actions.
Descarga secuencial a temporales del runner remoto, verifica con apksigner oficial,
calcula SHA-256 y compara sumas publicadas. Nunca escribe tokens ni los incluye en URLs.
"""
import hashlib
import json
import os
import pathlib
import re
import subprocess
import tempfile
import urllib.error
import urllib.parse
import urllib.request

class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new and urllib.parse.urlsplit(req.full_url).netloc != urllib.parse.urlsplit(newurl).netloc:
            new.remove_header("Authorization")
        return new

OPENER = urllib.request.build_opener(SafeRedirect())
REPO = os.environ["GITHUB_REPOSITORY"]
TOKEN = os.environ["GITHUB_TOKEN"]
TRUSTED = set(os.environ["APPROVED_PUBLIC_CERTIFICATES"].lower().split(","))
BASE = "https://api.github.com/repos/" + REPO + "/"
LIMIT = 600 * 1024 * 1024

def open_api(path, binary=False):
    req = urllib.request.Request(BASE + path, headers={
        "Authorization": "Bearer " + TOKEN,
        "Accept": "application/octet-stream" if binary else "application/vnd.github+json",
        "User-Agent": "remote-release-audit",
    })
    return OPENER.open(req, timeout=90)

def json_api(path):
    with open_api(path) as response:
        return json.load(response)

def releases():
    page = 1
    while True:
        items = json_api(f"releases?per_page=100&page={page}")
        yield from items
        if len(items) < 100:
            break
        page += 1

def checksums(release):
    values = {}
    for asset in release["assets"]:
        if asset["name"].lower() in ("sha256sums", "sha256sums.txt"):
            with open_api("releases/assets/" + str(asset["id"]), True) as response:
                text = response.read(65537)
            if len(text) > 65536:
                raise ValueError("Checksum manifest exceeds limit")
            for line in text.decode("utf-8").splitlines():
                match = re.fullmatch(r"([a-fA-F0-9]{64})\s+\*?(.+)", line)
                if match:
                    values[pathlib.PurePosixPath(match[2].strip()).name] = match[1].lower()
    return values

def main():
    report = {"repository": REPO, "commit": os.environ["GITHUB_SHA"], "apks": [], "errors": []}
    with tempfile.TemporaryDirectory(prefix="release-audit-") as tmp:
        apk = pathlib.Path(tmp) / "artifact.apk"
        for release in releases():
            try:
                sums = checksums(release)
            except Exception as exc:
                report["errors"].append({"tag": release["tag_name"], "stage": "checksums", "error": type(exc).__name__})
                sums = {}
            for asset in release["assets"]:
                if not asset["name"].endswith(".apk"):
                    continue
                item = {"tag": release["tag_name"], "name": asset["name"], "draft": release["draft"], "asset_id": asset["id"]}
                try:
                    digest = hashlib.sha256()
                    size = 0
                    with open_api("releases/assets/" + str(asset["id"]), True) as response, apk.open("wb") as output:
                        if "json" in response.headers.get("content-type", ""):
                            raise ValueError("Expected APK bytes, not metadata")
                        while True:
                            chunk = response.read(65536)
                            if not chunk:
                                break
                            size += len(chunk)
                            if size > LIMIT:
                                raise ValueError("APK exceeds size limit")
                            digest.update(chunk)
                            output.write(chunk)
                    item.update(bytes=size, sha256=digest.hexdigest())
                    if size != asset["size"]:
                        raise ValueError("Asset size mismatch")
                    if asset.get("digest") and asset["digest"] != "sha256:" + digest.hexdigest():
                        raise ValueError("API digest mismatch")
                    item["published_checksum_present"] = asset["name"] in sums
                    if asset["name"] in sums and sums[asset["name"]] != digest.hexdigest():
                        raise ValueError("Published checksum mismatch")
                    result = subprocess.run([os.environ["APKSIGNER"], "verify", "--verbose", "--print-certs", str(apk)],
                                            text=True, capture_output=True, timeout=120)
                    if result.returncode:
                        raise ValueError("apksigner cryptographic verification failed")
                    certs = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", result.stdout)
                    if not certs:
                        raise ValueError("Missing signer certificates")
                    item["signer_sha256"] = [c.lower() for c in certs]
                    item["signature_verified"] = True
                    item["public_signer_approved"] = all(c.lower() in TRUSTED for c in certs)
                    if not release["draft"] and not item["public_signer_approved"]:
                        raise ValueError("Unapproved signer on a public APK")
                    item["status"] = "quarantined" if release["draft"] else "verified"
                except Exception as exc:
                    item["status"] = "error"
                    item["error"] = str(exc) if isinstance(exc, ValueError) else type(exc).__name__
                    report["errors"].append({"tag": item["tag"], "name": item["name"], "error": item["error"]})
                finally:
                    apk.unlink(missing_ok=True)
                report["apks"].append(item)
                print(json.dumps(item, ensure_ascii=False), flush=True)
    if not report["apks"]:
        report["errors"].append({"error": "No APK assets inspected"})
    output = pathlib.Path(os.environ["AUDIT_OUTPUT"])
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    summary = {"apks": len(report["apks"]), "signature_verified": sum(a.get("signature_verified", False) for a in report["apks"]),
               "checksums_compared": sum(a.get("published_checksum_present", False) for a in report["apks"]), "errors": len(report["errors"])}
    print("SUMMARY " + json.dumps(summary))
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as f:
        f.write("### Auditoría criptográfica de publicaciones\n\n```json\n" + json.dumps(summary, indent=2) + "\n```\n")
    return 1 if report["errors"] else 0

if __name__ == "__main__":
    raise SystemExit(main())
