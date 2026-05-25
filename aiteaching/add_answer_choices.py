"""Inject answerChoices for steps where the AI asks the student a question.

Only the path-forward answer is shown — no decoys. The student taps it,
the chat records their answer, the step unlocks.
"""
import json
from pathlib import Path

PATH = Path(r"C:\Users\mcubi\Desktop\AITeachingApp\app\src\main\assets\rain_alert_tutorial.json")

CHOICES = {
    1: [
        {"en": "Android only, free, anonymous users",
         "vn": "Chỉ Android, miễn phí, người dùng ẩn danh"},
    ],
    2: [
        {"en": "Yes — let's use Kotlin",
         "vn": "Đồng ý — dùng Kotlin"},
    ],
    3: [
        {"en": "Yes — let's use Jetpack Compose",
         "vn": "Đồng ý — dùng Jetpack Compose"},
    ],
}

def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    for step in data["steps"]:
        n = step["stepNumber"]
        step["answerChoices"] = CHOICES.get(n, [])
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Added answerChoices to {len(CHOICES)} steps")

if __name__ == "__main__":
    main()
