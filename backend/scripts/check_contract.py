#!/usr/bin/env python3
"""
Contract drift check: every exported TypeScript interface in the frontend's src/types/api.ts that has a Java
record of the same name must expose exactly the same JSON property names (optional TS fields may be absent
from the Java record only if they are marked optional with '?').

Usage:  python scripts/check_contract.py [path/to/api.ts] [path/to/dto-dir]
Exit code 0 = in sync, 1 = drift.
"""
import re, sys, pathlib

here = pathlib.Path(__file__).resolve().parent
ts_path = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else here.parent.parent / "frontend/src/types/api.ts"
dto_dir = pathlib.Path(sys.argv[2]) if len(sys.argv) > 2 else here.parent / "src/main/java/com/example/camsat/dto"
web_dir = dto_dir.parent / "web"

ts = ts_path.read_text(encoding="utf-8")
ts = re.sub(r"/\*.*?\*/", "", ts, flags=re.S)
ts = re.sub(r"//.*", "", ts)

interfaces = {}
for m in re.finditer(r"export interface (\w+)(?:\s+extends\s+(\w+))?\s*\{(.*?)\n\}", ts, flags=re.S):
    name, parent, body = m.group(1), m.group(2), m.group(3)
    fields = {}
    for f in re.finditer(r"^\s*(\w+)(\?)?\s*:", body, flags=re.M):
        fields[f.group(1)] = bool(f.group(2))
    interfaces[name] = (parent, fields)

def ts_fields(name):
    parent, fields = interfaces[name]
    out = dict(ts_fields(parent)) if parent else {}
    out.update(fields)
    return out

def java_fields(name):
    for d in (dto_dir, web_dir):
        f = d / f"{name}.java"
        if f.exists():
            src = f.read_text(encoding="utf-8")
            m = re.search(r"public record " + name + r"\((.*?)\)\s*(?:implements[^{]*)?\{", src, flags=re.S)
            if not m:
                return None
            params = m.group(1)
            params = re.sub(r"@\w+(\([^)]*\))?", "", params)              # strip annotations
            depth, cur, parts = 0, "", []
            for ch in params:                                             # split on top-level commas (generics!)
                if ch == "<": depth += 1
                if ch == ">": depth -= 1
                if ch == "," and depth == 0: parts.append(cur); cur = ""
                else: cur += ch
            if cur.strip(): parts.append(cur)
            return [p.split()[-1] for p in parts if p.strip()]
    return None

bad = 0
checked = 0
for name in interfaces:
    jf = java_fields(name)
    if jf is None:
        continue                       # type-only alias / no Java counterpart (e.g. request-only helpers)
    checked += 1
    tf = ts_fields(name)
    missing_in_java = [k for k, optional in tf.items() if k not in jf and not optional]
    extra_in_java = [k for k in jf if k not in tf]
    if missing_in_java or extra_in_java:
        bad += 1
        print(f"DRIFT {name}: missing in Java={missing_in_java} extra in Java (not in TS)={extra_in_java}")
    else:
        print(f"ok    {name} ({len(jf)} fields)")
print(f"\nchecked {checked} interfaces, {bad} with drift")
sys.exit(1 if bad else 0)
