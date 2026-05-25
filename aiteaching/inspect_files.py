import json
data = json.load(open(r"C:\Users\mcubi\Desktop\AITeachingApp\app\src\main\assets\rain_alert_tutorial.json", encoding="utf-8"))
for s in data["steps"]:
    files = [(cs["fileName"], len(cs["code"].split("\n"))) for cs in s["codeSnippets"]]
    pretty = ", ".join(f"{n} ({lc}L)" for n, lc in files) if files else "no code"
    print(f"Step {s['stepNumber']:2d}: {pretty}")
