"""Strip "- Bug" / "- Bug Fix" suffixes from snippet fileNames so that bug-fix
snippets merge naturally with their original file in the cumulative code panel.
"""
import json, re
from pathlib import Path

PATH = Path(r"C:\Users\mcubi\Desktop\AITeachingApp\app\src\main\assets\rain_alert_tutorial.json")

SUFFIX_RE = re.compile(r"\s*[-–—]\s*Bug(?:\s*Fix)?\s*$", re.IGNORECASE)

def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    renamed = 0
    for step in data["steps"]:
        for snip in step["codeSnippets"]:
            new = SUFFIX_RE.sub("", snip["fileName"]).strip()
            if new != snip["fileName"]:
                print(f"Step {step['stepNumber']}: {snip['fileName']!r} -> {new!r}")
                snip["fileName"] = new
                renamed += 1
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nRenamed {renamed} snippet file names")

if __name__ == "__main__":
    main()
