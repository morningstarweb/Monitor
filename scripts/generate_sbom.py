#!/usr/bin/env python3
"""Generate a CycloneDX-ish software bill of materials for Monitor.

Design constraints (intentional):
  * Python 3.11+ standard library only.
  * NO network access. Nothing is downloaded and no registry is queried.
  * Licenses are populated strictly from local metadata when present on disk:
    - desktop/Cargo.lock + local Cargo registry/workspace Cargo.toml manifests
    - cloud/package-lock.json + local cloud/node_modules/*/package.json files
    - android/gradle/libs.versions.toml + local Gradle cache POM manifests
    If no local license metadata exists on disk, the component is reported
    honestly as UNKNOWN rather than being guessed.

Sources:
  * desktop/Cargo.lock               -> Cargo packages (checksums included)
  * cloud/package-lock.json          -> npm packages (license when present)
  * android/gradle/libs.versions.toml -> Maven coordinates declared in the catalog
  * config/brand.toml                -> product version (optional)

This output is "CycloneDX-ish": it follows the CycloneDX 1.5 shape for the
fields we can honestly populate, but it is NOT a validated CycloneDX document
and must not be represented as one without a proper validation/enrichment step.
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import sys
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any

TOOL_NAME = "monitor-generate-sbom"
TOOL_VERSION = "0.1.0"
UNKNOWN = "UNKNOWN"

INPUTS = {
    "cargo": "desktop/Cargo.lock",
    "npm": "cloud/package-lock.json",
    "gradle": "android/gradle/libs.versions.toml",
}
OPTIONAL_INPUTS = {"brand": "config/brand.toml"}


class SbomError(Exception):
    """Input or parsing failure that must fail the run."""


def _strip_quotes(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
        return value[1:-1]
    return value


def _cargo_field(block: str, key: str) -> str | None:
    match = re.search(rf"(?m)^{re.escape(key)}\s*=\s*\"([^\"]*)\"", block)
    return match.group(1) if match else None


def _cargo_registry_roots() -> list[Path]:
    cargo_home = Path(os.environ.get("CARGO_HOME", Path.home() / ".cargo"))
    src_dir = cargo_home / "registry" / "src"
    if not src_dir.is_dir():
        return []
    return [child for child in sorted(src_dir.iterdir()) if child.is_dir()]


def _workspace_crate_licenses(root: Path) -> dict[str, str]:
    licenses: dict[str, str] = {}
    desktop_dir = root / "desktop"
    ws_license: str | None = None
    ws_toml = desktop_dir / "Cargo.toml"
    if ws_toml.is_file():
        try:
            with ws_toml.open("rb") as handle:
                ws_data = tomllib.load(handle)
            raw_ws = (ws_data.get("workspace", {}) or {}).get("package", {}) or {}
            if isinstance(raw_ws.get("license"), str) and raw_ws["license"].strip():
                ws_license = raw_ws["license"].strip()
        except (OSError, tomllib.TOMLDecodeError):
            pass
    crates_dir = desktop_dir / "crates"
    if crates_dir.is_dir():
        for manifest in sorted(crates_dir.glob("*/Cargo.toml")):
            try:
                with manifest.open("rb") as handle:
                    pkg = tomllib.load(handle).get("package", {}) or {}
                pkg_name = pkg.get("name")
                lic = pkg.get("license")
                if isinstance(lic, dict) and lic.get("workspace") is True:
                    lic = ws_license
                if isinstance(pkg_name, str) and isinstance(lic, str) and lic.strip():
                    licenses[pkg_name] = lic.strip()
            except (OSError, tomllib.TOMLDecodeError):
                continue
    return licenses


def _resolve_cargo_local_license(
    name: str,
    version: str | None,
    workspace_licenses: dict[str, str],
    registry_roots: list[Path],
) -> tuple[str | None, str]:
    if name in workspace_licenses:
        return workspace_licenses[name], "FROM_WORKSPACE_MANIFEST"
    if version:
        folder_name = f"{name}-{version}"
        for reg_root in registry_roots:
            manifest = reg_root / folder_name / "Cargo.toml"
            if manifest.is_file():
                try:
                    with manifest.open("rb") as handle:
                        pkg = tomllib.load(handle).get("package", {}) or {}
                    lic = pkg.get("license")
                    if isinstance(lic, str) and lic.strip():
                        return lic.strip(), "FROM_LOCAL_CARGO_CACHE"
                    lic_file = pkg.get("license-file")
                    if isinstance(lic_file, str) and lic_file.strip():
                        return f"See {lic_file.strip()}", "FROM_LOCAL_CARGO_CACHE"
                except (OSError, tomllib.TOMLDecodeError):
                    continue
    return None, "UNKNOWN - Cargo.lock and local Cargo cache lacked license metadata; no network lookup performed"


def parse_cargo_lock(path: Path, root: Path) -> list[dict[str, Any]]:
    text = path.read_text(encoding="utf-8")
    blocks = text.split("[[package]]")[1:]
    if not blocks:
        raise SbomError(f"{path}: no [[package]] entries found")
    workspace_licenses = _workspace_crate_licenses(root)
    registry_roots = _cargo_registry_roots()
    components: list[dict[str, Any]] = []
    for block in blocks:
        name = _cargo_field(block, "name")
        version = _cargo_field(block, "version")
        source = _cargo_field(block, "source")
        checksum = _cargo_field(block, "checksum")
        if not name:
            continue
        purl = f"pkg:cargo/{name}@{version}" if version else f"pkg:cargo/{name}"
        license_name, license_status = _resolve_cargo_local_license(
            name, version, workspace_licenses, registry_roots
        )
        props = [
            {"name": "monitor:sourceFile", "value": "desktop/Cargo.lock"},
            {"name": "monitor:licenseStatus", "value": license_status},
        ]
        if source:
            props.append({"name": "monitor:cargoSource", "value": source})
        component: dict[str, Any] = {
            "type": "library",
            "bom-ref": purl,
            "name": name,
            "version": version or UNKNOWN,
            "purl": purl,
            "licenses": [{"license": {"name": license_name or UNKNOWN}}],
            "properties": props,
        }
        if checksum:
            component["hashes"] = [{"alg": "SHA-256", "content": checksum}]
        components.append(component)
    return components


def _license_name(raw: Any) -> str | None:
    if isinstance(raw, str) and raw.strip():
        return raw.strip()
    if isinstance(raw, dict):
        for key in ("type", "name"):
            value = raw.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return None


def parse_npm_lock(path: Path, root: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    packages = data.get("packages")
    if not isinstance(packages, dict):
        raise SbomError(f"{path}: no 'packages' object found (unsupported lockfileVersion)")
    cloud_dir = root / "cloud"
    components: list[dict[str, Any]] = []
    for key, entry in sorted(packages.items()):
        if not isinstance(entry, dict) or key == "":
            continue  # "" is the project root, not a dependency
        name = entry.get("name") or key.rsplit("node_modules/", 1)[-1]
        version = entry.get("version")
        if not isinstance(name, str) or not name:
            continue
        purl = f"pkg:npm/{name}@{version}" if version else f"pkg:npm/{name}"
        license_name = _license_name(entry.get("license"))
        license_status = "FROM_LOCKFILE - unverified; confirm against upstream LICENSE files"
        if not license_name:
            pkg_json = cloud_dir / key / "package.json"
            if pkg_json.is_file():
                try:
                    with pkg_json.open("r", encoding="utf-8") as pkg_handle:
                        license_name = _license_name(json.load(pkg_handle).get("license"))
                    if license_name:
                        license_status = "FROM_LOCAL_NODE_MODULES - confirm against upstream LICENSE files"
                except (OSError, json.JSONDecodeError):
                    pass
        if not license_name:
            license_status = "UNKNOWN - lockfile entry and local node_modules lacked license metadata"
        props = [
            {"name": "monitor:sourceFile", "value": "cloud/package-lock.json"},
            {"name": "monitor:licenseStatus", "value": license_status},
        ]
        if entry.get("dev") is True:
            props.append({"name": "monitor:scope", "value": "dev"})
        component: dict[str, Any] = {
            "type": "library",
            "bom-ref": purl,
            "name": name,
            "version": str(version) if version else UNKNOWN,
            "purl": purl,
            "licenses": [{"license": {"name": license_name or UNKNOWN}}],
            "properties": props,
        }
        integrity = entry.get("integrity")
        if isinstance(integrity, str) and integrity.startswith("sha256-"):
            component["hashes"] = [{"alg": "SHA-256", "content": integrity[7:]}]
        components.append(component)
    return components


def _gradle_bom_versions(
    libraries: dict[str, Any], versions: dict[str, Any], gradle_home: Path
) -> dict[tuple[str, str], str]:
    managed: dict[tuple[str, str], str] = {}
    for spec in libraries.values():
        if not isinstance(spec, dict):
            continue
        group = spec.get("group")
        name = spec.get("name")
        if not isinstance(group, str) or not isinstance(name, str) or not name.endswith("-bom"):
            continue
        ver = spec.get("version")
        if isinstance(ver, dict) and isinstance(ver.get("ref"), str):
            ver = versions.get(ver["ref"])
        if not isinstance(ver, str) or not ver:
            continue
        bom_dir = gradle_home / "caches" / "modules-2" / "files-2.1" / group / name / ver
        if not bom_dir.is_dir():
            continue
        for pom_path in sorted(bom_dir.glob("*/*.pom")):
            try:
                tree = ET.parse(pom_path)
                for elem in tree.getroot().iter():
                    if elem.tag.endswith("dependencyManagement"):
                        for dep in elem.iter():
                            if not dep.tag.endswith("dependency"):
                                continue
                            dg = dn = dv = None
                            for child in dep:
                                if child.tag.endswith("groupId") and child.text:
                                    dg = child.text.strip()
                                elif child.tag.endswith("artifactId") and child.text:
                                    dn = child.text.strip()
                                elif child.tag.endswith("version") and child.text:
                                    dv = child.text.strip()
                            if dg and dn and dv:
                                managed[(dg, dn)] = dv
            except (OSError, ET.ParseError):
                continue
    return managed


def _resolve_gradle_local_license(
    group: str, name: str, version: str | None, gradle_home: Path
) -> tuple[str | None, str]:
    if not version:
        return None, "UNKNOWN - version omitted (managed via BOM) and not resolved locally"
    module_dir = gradle_home / "caches" / "modules-2" / "files-2.1" / group / name / str(version)
    if module_dir.is_dir():
        for pom_path in sorted(module_dir.glob("*/*.pom")):
            try:
                tree = ET.parse(pom_path)
                found: list[str] = []
                for elem in tree.getroot().iter():
                    if elem.tag.endswith("licenses"):
                        for lic in elem:
                            if lic.tag.endswith("license"):
                                for child in lic:
                                    if child.tag.endswith("name") and child.text and child.text.strip():
                                        found.append(child.text.strip())
                if found:
                    return " OR ".join(dict.fromkeys(found)), "FROM_LOCAL_GRADLE_POM_CACHE"
            except (OSError, ET.ParseError):
                continue
    return None, "UNKNOWN - Gradle POM not cached locally with license metadata; no network lookup performed"


def parse_gradle_catalog(path: Path, _root: Path) -> list[dict[str, Any]]:
    with path.open("rb") as handle:
        data = tomllib.load(handle)
    versions = data.get("versions", {}) or {}
    libraries = data.get("libraries", {}) or {}
    if not libraries:
        raise SbomError(f"{path}: no [libraries] entries found")
    gradle_home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    bom_versions = _gradle_bom_versions(libraries, versions, gradle_home)
    components: list[dict[str, Any]] = []
    for alias, spec in sorted(libraries.items()):
        if not isinstance(spec, dict):
            continue
        group = spec.get("group")
        name = spec.get("name")
        if not isinstance(group, str) or not isinstance(name, str):
            continue
        version = spec.get("version")
        ref = spec.get("version", {}) if isinstance(spec.get("version"), dict) else None
        if ref and isinstance(ref.get("ref"), str):
            version = versions.get(ref["ref"])
        if not version:
            version = bom_versions.get((group, name))
        purl = f"pkg:maven/{group}/{name}@{version}" if version else f"pkg:maven/{group}/{name}"
        license_name, license_status = _resolve_gradle_local_license(
            group, name, version if isinstance(version, str) else None, gradle_home
        )
        components.append(
            {
                "type": "library",
                "bom-ref": purl,
                "name": name,
                "version": str(version) if version else UNKNOWN,
                "purl": purl,
                "licenses": [{"license": {"name": license_name or UNKNOWN}}],
                "properties": [
                    {"name": "monitor:sourceFile", "value": "android/gradle/libs.versions.toml"},
                    {"name": "monitor:catalogAlias", "value": alias},
                    {"name": "monitor:licenseStatus", "value": license_status},
                ],
            }
        )
    return components


def read_product_version(path: Path) -> str:
    try:
        with path.open("rb") as handle:
            data = tomllib.load(handle)
        brand = data.get("brand", {}) or {}
        version = brand.get("version")
        if isinstance(version, str) and version.strip():
            return version.strip()
    except (OSError, tomllib.TOMLDecodeError) as exc:
        print(f"warning: could not read {path}: {exc}", file=sys.stderr)
    return UNKNOWN


def _timestamp() -> str:
    epoch = os.environ.get("SOURCE_DATE_EPOCH")
    if epoch and epoch.isdigit():
        return _dt.datetime.fromtimestamp(int(epoch), tz=_dt.timezone.utc).isoformat()
    return _dt.datetime.now(tz=_dt.timezone.utc).replace(microsecond=0).isoformat()


def build_sbom(root: Path, allow_missing: bool) -> dict[str, Any]:
    components: list[dict[str, Any]] = []
    missing: list[str] = []
    for key, relative in INPUTS.items():
        path = root / relative
        if not path.is_file():
            if allow_missing:
                print(f"warning: missing input {relative}; skipping", file=sys.stderr)
                missing.append(relative)
                continue
            raise SbomError(f"missing required input: {relative}")
        parser = {
            "cargo": parse_cargo_lock,
            "npm": parse_npm_lock,
            "gradle": parse_gradle_catalog,
        }[key]
        components.extend(parser(path, root))

    # Deduplicate on bom-ref, keeping the first (deterministic) entry.
    seen: set[str] = set()
    unique: list[dict[str, Any]] = []
    for component in components:
        ref = component["bom-ref"]
        if ref in seen:
            continue
        seen.add(ref)
        unique.append(component)
    unique.sort(key=lambda item: (item["name"], item["version"]))

    known = sum(
        1
        for item in unique
        if item["licenses"][0]["license"]["name"] != UNKNOWN
    )
    brand_path = root / OPTIONAL_INPUTS["brand"]
    product_version = read_product_version(brand_path) if brand_path.is_file() else UNKNOWN

    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "version": 1,
        "metadata": {
            "timestamp": _timestamp(),
            "tools": [
                {"vendor": "Monitor", "name": TOOL_NAME, "version": TOOL_VERSION}
            ],
            "component": {
                "type": "application",
                "name": "Monitor",
                "version": product_version,
                "bom-ref": f"pkg:generic/monitor@{product_version}",
            },
            "properties": [
                {"name": "monitor:cyclonedxValidation", "value": "NOT VALIDATED - CycloneDX-ish shape only"},
                {"name": "monitor:networkAccess", "value": "none"},
                {"name": "monitor:componentsTotal", "value": str(len(unique))},
                {"name": "monitor:licensesKnownFromLockfile", "value": str(known)},
                {"name": "monitor:licensesUnknown", "value": str(len(unique) - known)},
                {"name": "monitor:missingInputs", "value": ",".join(missing) or "none"},
                {
                    "name": "monitor:licenseDisclaimer",
                    "value": "License values are informational and unverified. Consult upstream LICENSE/NOTICE files and legal review before distribution.",
                },
            ],
        },
        "components": unique,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parent.parent,
        help="repository root (default: parent of the scripts directory)",
    )
    parser.add_argument(
        "--out",
        default="-",
        help="output path, or '-' for stdout (default: '-')",
    )
    parser.add_argument(
        "--allow-missing",
        action="store_true",
        help="skip missing input files with a warning instead of failing",
    )
    args = parser.parse_args(argv)
    root = args.root.resolve()
    try:
        sbom = build_sbom(root, args.allow_missing)
    except (SbomError, OSError, ValueError, json.JSONDecodeError, tomllib.TOMLDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    payload = json.dumps(sbom, indent=2, ensure_ascii=False, sort_keys=False) + "\n"
    if args.out == "-":
        sys.stdout.write(payload)
    else:
        try:
            Path(args.out).write_text(payload, encoding="utf-8")
        except OSError as exc:
            print(f"error: cannot write {args.out}: {exc}", file=sys.stderr)
            return 2
        print(
            f"wrote {args.out}: {len(sbom['components'])} components, "
            f"{len(sbom['components']) - int(dict((p['name'], p['value']) for p in sbom['metadata']['properties'])['monitor:licensesKnownFromLockfile'])} licenses UNKNOWN",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
