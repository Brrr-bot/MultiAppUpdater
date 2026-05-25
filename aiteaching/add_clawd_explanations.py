"""Inject Clawd interstitial explanations into rain_alert_tutorial.json.

Each step gets a `clawdExplanation` that:
  - explains in plain English what we just got AI to build,
  - teases the next step,
  - wraps code-ish words in `backticks` so the syntax highlighter colours them.

The goal is to teach *coder thinking*, not Kotlin syntax.
"""

import json
from pathlib import Path

PATH = Path(r"C:\Users\mcubi\Desktop\AITeachingApp\app\src\main\assets\rain_alert_tutorial.json")

# Indexed by stepNumber (1..16). Step 16 (final) is skipped.
EXPLANATIONS = {
    1: {
        "en": "Nice — you just learned what Android development actually IS. Apps are just instructions written in a language called `Kotlin`. You don't need to write it yourself, the `AI` does. Your job is to ask the right questions. Next up: the building blocks of `Kotlin` — things like `val` (a value that never changes) and `var` (one that can).",
        "vn": "Tuyệt — bạn vừa hiểu Android development thực sự LÀ gì. App chỉ là tập lệnh viết bằng ngôn ngữ `Kotlin`. Bạn không cần tự viết, `AI` viết hộ. Việc của bạn là đặt câu hỏi đúng. Tiếp theo: các viên gạch của `Kotlin` — như `val` (giá trị không đổi) và `var` (giá trị thay đổi được)."
    },
    2: {
        "en": "Good — you saw how coders store information. `val tempC = 25.5` means \"remember this number forever.\" `var counter = 0` means \"keep this number, but I might change it.\" These are like sticky notes for the app. Next: we set up an empty Android `Project` so the AI has somewhere to put the code.",
        "vn": "Tốt — bạn đã thấy cách coder lưu thông tin. `val tempC = 25.5` nghĩa là \"nhớ con số này mãi mãi.\" `var counter = 0` nghĩa là \"giữ con số này, nhưng tôi có thể đổi.\" Đó là những tờ giấy ghi chú của app. Tiếp: tạo `Project` Android trống để AI có chỗ đặt code."
    },
    3: {
        "en": "Done — empty project created. Think of a `Project` as a folder with rules: \"all the files for this app live here.\" The AI knows where to put things now. Next: `Jetpack Compose` — the modern way to draw what users see on screen, with code instead of drag-and-drop.",
        "vn": "Xong — project trống đã tạo. Hãy nghĩ `Project` như một thư mục có luật: \"tất cả file của app sống ở đây.\" Giờ AI biết đặt mọi thứ vào đâu. Tiếp: `Jetpack Compose` — cách hiện đại để vẽ những gì người dùng thấy, dùng code thay vì kéo-thả."
    },
    4: {
        "en": "You just met `Compose`. Each piece of UI is a `function` marked with `@Composable`. Want a button? Call `Button()`. Want text? Call `Text()`. The AI snaps these blocks together like Lego. Next: we ask the AI to design the main screen for our rain-alert app.",
        "vn": "Bạn vừa làm quen với `Compose`. Mỗi mảnh UI là một `function` đánh dấu `@Composable`. Muốn nút? Gọi `Button()`. Muốn chữ? Gọi `Text()`. AI lắp ráp như Lego. Tiếp: nhờ AI thiết kế màn hình chính cho app cảnh báo mưa."
    },
    5: {
        "en": "Look at that — a screen designed with words. You described what you wanted (\"a button that says Report Rain\"), the AI wrote the `Compose` code. Notice the pattern: describe → AI builds → you check. Next: how the phone figures out WHERE you are, using `Location` services.",
        "vn": "Nhìn xem — một màn hình thiết kế bằng lời. Bạn mô tả (\"nút ghi Báo Mưa\"), AI viết code `Compose`. Quy luật: mô tả → AI làm → bạn kiểm tra. Tiếp: điện thoại biết bạn Ở ĐÂU bằng dịch vụ `Location`."
    },
    6: {
        "en": "GPS unlocked. The app can now read latitude/longitude from the phone. Coders call this \"asking the device for data\" — and we always ask politely with `permission`. Next: `Firebase` — a free Google service that stores rain reports from EVERY user in one shared database.",
        "vn": "Đã mở khoá GPS. App đọc được kinh-vĩ độ. Coder gọi đây là \"xin dữ liệu từ thiết bị\" — luôn xin lịch sự bằng `permission`. Tiếp: `Firebase` — dịch vụ Google miễn phí lưu báo cáo mưa từ MỌI user vào một database chung."
    },
    7: {
        "en": "`Firebase` is now your app's brain in the cloud. Instead of every phone keeping its own data, they all read/write to the same place. This is how `WhatsApp`, `Instagram`, basically every app works. Next: connect the Report Rain button to actually SEND a report.",
        "vn": "`Firebase` giờ là bộ não trên mây của app. Thay vì mỗi điện thoại giữ data riêng, tất cả đọc/ghi cùng một chỗ. `WhatsApp`, `Instagram`… app nào cũng vậy. Tiếp: nối nút Báo Mưa để thực sự GỬI báo cáo."
    },
    8: {
        "en": "Boom — when the user taps the button, the app packs up their `location` and the `time`, then sends it to `Firebase`. This is your first real \"action.\" Coders call this an event handler — \"when X happens, do Y.\" Next: how to ping OTHER users' phones when rain is reported nearby.",
        "vn": "Bùm — khi user nhấn nút, app đóng gói `location` + `time` và gửi lên `Firebase`. Đây là \"hành động\" thật đầu tiên. Coder gọi là event handler — \"khi X xảy ra, làm Y.\" Tiếp: cách ping điện thoại NGƯỜI KHÁC khi có báo mưa gần đó."
    },
    9: {
        "en": "Push notifications wired up! `FCM` (Firebase Cloud Messaging) is the postman — it delivers tiny messages to phones even when the app is closed. Notice how you're never writing networking code by hand — the AI handles the plumbing. Next: show those rain reports on a `Map`.",
        "vn": "Push notification đã nối! `FCM` (Firebase Cloud Messaging) là người đưa thư — gửi tin nhỏ tới điện thoại kể cả khi app đóng. Bạn không hề tự viết code mạng — AI lo phần ống nước. Tiếp: hiện các báo mưa trên `Map`."
    },
    10: {
        "en": "Map view live! Every rain report becomes a little pin. Users see exactly WHERE it's raining around them. Pattern again: data lives in `Firebase`, the `Map` just reads and displays it. Next: we wire all the pieces together and test it end-to-end.",
        "vn": "Bản đồ đã sống! Mỗi báo mưa thành một ghim. User thấy CHÍNH XÁC chỗ đang mưa quanh mình. Quy luật: data ở `Firebase`, `Map` chỉ đọc và hiển thị. Tiếp: nối tất cả mảnh ghép và test từ đầu đến cuối."
    },
    11: {
        "en": "Everything's connected and the app works! You went from zero to a real, working app by ASKING the AI. This is the coder superpower — knowing WHAT to ask and HOW to check the answer. Next: a quick final review of what we built.",
        "vn": "Mọi thứ đã nối và app chạy được! Bạn đi từ số 0 đến app thật, chỉ bằng cách HỎI AI. Đây là siêu năng lực của coder — biết HỎI GÌ và biết KIỂM TRA câu trả lời. Tiếp: nhìn lại nhanh những gì đã làm."
    },
    12: {
        "en": "Quick recap: a button, a `Map`, a database, notifications — all the pieces of a real social app. Same recipe builds Uber, Strava, Pokémon GO. But real apps have BUGS. Real coders spend most of their time fixing them. Next: your first crash — a `SecurityException`.",
        "vn": "Tóm tắt: một nút, một `Map`, một database, notification — tất cả mảnh của app xã hội thật. Công thức này dựng được Uber, Strava, Pokémon GO. Nhưng app thật có BUG. Coder thật dành phần lớn thời gian sửa bug. Tiếp: lỗi đầu tiên — `SecurityException`."
    },
    13: {
        "en": "Bug squashed! `SecurityException` happened because we asked for `location` without telling Android \"hey, this app needs that permission.\" The AI fixed it once you pasted the error. Lesson: don't panic at red text — copy it, paste it, ask. Next bug: `NullPointerException`.",
        "vn": "Đã diệt bug! `SecurityException` xảy ra vì ta xin `location` mà chưa khai báo với Android. AI sửa ngay khi bạn dán lỗi. Bài học: thấy chữ đỏ đừng hoảng — copy, paste, hỏi. Bug tiếp: `NullPointerException`."
    },
    14: {
        "en": "Another one down! `NullPointerException` is the most famous bug in coding — it means \"you tried to use something that wasn't there.\" Like trying to read a book that doesn't exist. The fix: check first with an `if` before using it. Next: a notification bug specific to Android 12+.",
        "vn": "Diệt thêm bug! `NullPointerException` là bug nổi tiếng nhất — nghĩa là \"bạn dùng cái không tồn tại.\" Như đọc cuốn sách không có. Cách sửa: kiểm tra bằng `if` trước khi dùng. Tiếp: bug notification chỉ có ở Android 12+."
    },
    15: {
        "en": "All three bugs crushed! Notice the rhythm: app breaks → read the error → ask the AI → app works. That loop is 90% of being a real developer. You don't memorise code, you debug it. Next: a celebration screen because you've actually finished a course.",
        "vn": "Đập tan cả ba bug! Nhịp điệu: app vỡ → đọc lỗi → hỏi AI → app chạy. Vòng lặp đó là 90% công việc developer thật. Không cần thuộc code, chỉ cần debug. Tiếp: màn hình ăn mừng vì bạn vừa hoàn thành khoá học thật."
    },
}

def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    for step in data["steps"]:
        n = step["stepNumber"]
        if n in EXPLANATIONS:
            step["clawdExplanation"] = EXPLANATIONS[n]
        else:
            step["clawdExplanation"] = {"en": "", "vn": ""}
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {len(EXPLANATIONS)} explanations into {PATH.name}")

if __name__ == "__main__":
    main()
