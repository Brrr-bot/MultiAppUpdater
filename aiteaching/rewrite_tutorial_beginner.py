"""Full rewrite of rain_alert_tutorial.json for absolute beginners.

The student has never coded before, has never used AI to make an app, and
doesn't know what Kotlin/Compose/Firebase are yet. Each step teaches one
concept in plain language, with the AI naturally introducing the next term.

Code snippets are left as-is (they're real code); we rewrite all the prose
that surrounds them.
"""
import json
from pathlib import Path

PATH = Path(r"C:\Users\mcubi\Desktop\AITeachingApp\app\src\main\assets\rain_alert_tutorial.json")

# ---------------------------------------------------------------------------
# Step content — each entry overrides title/instruction/promptText/
# promptRationale/aiResponse/clawdExplanation for that step.
# Code snippets keep their existing structure but get fresh line explanations.
# ---------------------------------------------------------------------------

STEPS = {

# ============================ STEP 1: THE IDEA =============================
1: {
    "title_en": "Tell the AI what you want to build",
    "title_vn": "Nói AI bạn muốn xây gì",
    "instruction_en":
        "When you want to build an app, you don't need to know HOW to build it. "
        "You just need to say WHAT you want it to do, in plain words. The AI will "
        "figure out the rest. Our idea: an app where people tap a button to say "
        "\"it's raining here,\" and other users nearby get an alert.",
    "instruction_vn":
        "Khi bạn muốn xây app, bạn không cần biết XÂY thế nào. Bạn chỉ cần nói app "
        "LÀM GÌ, bằng lời thường. AI sẽ lo phần còn lại. Ý tưởng: app cho người dùng "
        "nhấn nút \"trời đang mưa ở đây,\" và những người dùng gần đó nhận được "
        "thông báo.",
    "promptText":
        "Hi! I've never built an app before and I have zero programming experience. "
        "I want to make an app where users can tap a button when they see rain in "
        "their area, and other users nearby get an alert on their phone. Can you "
        "help me build this? What decisions do I need to make first?",
    "rationale_en":
        "We start in plain English — no jargon. The AI's first job is to ask us "
        "the right QUESTIONS (what platform, what language, what features). We "
        "learn by answering those questions one at a time.",
    "rationale_vn":
        "Bắt đầu bằng tiếng Anh thường — không thuật ngữ. Việc đầu tiên của AI là "
        "ĐẶT câu hỏi đúng (nền tảng nào, ngôn ngữ nào, tính năng gì). Bạn học bằng "
        "cách trả lời từng câu hỏi.",
    "ai_en":
        "Welcome! Love this idea — a community rain alert app. Before we write "
        "anything, I need to know a few things:\n\n"
        "(1) Which phones — Android, iPhone, or both? Each is a different world.\n"
        "(2) Free or paid app?\n"
        "(3) Do users sign up, or anonymous?\n\n"
        "I'd recommend Android first: the tools are free, the platform is the "
        "world's biggest, and you can install your own app without paying Apple's "
        "$99/year fee. Let me know which phones you want to target.",
    "ai_vn":
        "Chào bạn! Ý tưởng hay — app cảnh báo mưa cộng đồng. Trước khi viết code, "
        "tôi cần biết:\n\n"
        "(1) Điện thoại nào — Android, iPhone, hay cả hai?\n"
        "(2) App miễn phí hay tính phí?\n"
        "(3) User cần đăng ký hay dùng ẩn danh?\n\n"
        "Tôi đề xuất Android trước: công cụ miễn phí, nền tảng lớn nhất thế giới, "
        "bạn có thể tự cài app lên máy mà không cần trả $99/năm cho Apple. Bạn "
        "muốn nhắm điện thoại nào?",
    "clawd_en":
        "Nice — you just started a real app project the way pros do. They don't "
        "open a code editor first. They DESCRIBE what they want and let the AI "
        "ask the right questions. Next: picking a language. Don't worry, you "
        "don't need to know what one is yet — you'll ask `Claude` to recommend.",
    "clawd_vn":
        "Tuyệt — bạn vừa khởi đầu một dự án app thật theo cách dân chuyên dùng. "
        "Họ không mở trình soạn code trước. Họ MÔ TẢ thứ họ muốn và để AI hỏi "
        "câu đúng. Tiếp: chọn ngôn ngữ. Không lo, bạn chưa cần biết nó là gì — "
        "bạn sẽ nhờ `Claude` đề xuất.",
},

# ====================== STEP 2: CHOOSE A LANGUAGE ==========================
2: {
    "title_en": "Pick a programming language",
    "title_vn": "Chọn một ngôn ngữ lập trình",
    "instruction_en":
        "A programming language is the words you use to tell the computer what "
        "to do — like English vs Vietnamese, but for machines. Different languages "
        "do similar jobs but read differently. The AI is asking which one we "
        "want. We don't know yet — so we ASK the AI to recommend one, with reasons.",
    "instruction_vn":
        "Ngôn ngữ lập trình là từ ngữ bạn dùng để bảo máy tính làm gì — như "
        "tiếng Anh vs tiếng Việt, nhưng cho máy. Các ngôn ngữ làm việc tương tự "
        "nhưng đọc khác nhau. AI hỏi ta muốn dùng ngôn ngữ nào. Ta chưa biết — "
        "nên NHỜ AI đề xuất, kèm lý do.",
    "promptText":
        "I want it on Android phones only. I don't know any programming languages "
        "at all. Which language should we use, and why? Please explain in simple "
        "words, like I'm completely new.",
    "rationale_en":
        "Never pretend you know something. Ask the AI to recommend AND explain "
        "WHY. The 'why' is the part that teaches you.",
    "rationale_vn":
        "Đừng bao giờ giả vờ biết. Nhờ AI đề xuất VÀ giải thích VÌ SAO. Phần "
        "'vì sao' là phần dạy bạn.",
    "ai_en":
        "Good question. For Android, there are two main languages:\n\n"
        "• **Java** — older, used in lots of legacy apps, more wordy.\n"
        "• **Kotlin** — modern (2011), what Google officially recommends since 2019.\n\n"
        "For a first app I strongly suggest **Kotlin** because:\n"
        "(1) Needs about 40% less code than Java to do the same job — less typing, "
        "less reading.\n"
        "(2) Catches a whole class of crashes (called 'null pointer exceptions') "
        "before you even run the app.\n"
        "(3) Most new Android apps use it: Pinterest, Trello, Square, Duolingo.\n\n"
        "Want me to set up a Kotlin project?",
    "ai_vn":
        "Câu hỏi tốt. Cho Android có hai ngôn ngữ chính:\n\n"
        "• **Java** — cũ hơn, dùng trong nhiều app cũ, dài dòng hơn.\n"
        "• **Kotlin** — hiện đại (2011), được Google khuyến nghị chính thức từ 2019.\n\n"
        "Cho app đầu tay tôi đề xuất **Kotlin** vì:\n"
        "(1) Cần ít hơn ~40% code so với Java — gõ ít, đọc ít.\n"
        "(2) Bắt được cả lớp crash (gọi là 'null pointer exception') trước khi chạy.\n"
        "(3) Hầu hết app Android mới đều dùng: Pinterest, Trello, Square, Duolingo.\n\n"
        "Bạn muốn tôi tạo project Kotlin chứ?",
    "clawd_en":
        "You picked `Kotlin`. Notice you didn't pick it because you knew what it "
        "was. You picked it because the AI gave you reasons that made sense. "
        "That's how real developers choose tools every day. Next: how the AI "
        "will actually DRAW the screens you'll see in the app.",
    "clawd_vn":
        "Bạn chọn `Kotlin`. Chú ý: bạn không chọn vì biết nó là gì. Bạn chọn vì "
        "AI cho lý do hợp lý. Dev thật chọn công cụ kiểu này hàng ngày. Tiếp: "
        "AI sẽ VẼ những màn hình bạn thấy trong app thế nào.",
},

# ===================== STEP 3: CHOOSE HOW TO DRAW SCREENS ===================
3: {
    "title_en": "Pick a way to build the screens",
    "title_vn": "Chọn cách xây dựng màn hình",
    "instruction_en":
        "An app has screens — the things you tap and look at. Developers can "
        "either DRAW them in separate layout files (the old way) or DESCRIBE "
        "them inside the code (the new way). Two approaches with different "
        "trade-offs. Let's ask the AI to compare.",
    "instruction_vn":
        "App có màn hình — thứ bạn chạm vào và nhìn vào. Dev có thể VẼ chúng "
        "trong file layout riêng (cách cũ) hoặc MÔ TẢ chúng trong code (cách "
        "mới). Hai cách, đánh đổi khác nhau. Hỏi AI so sánh.",
    "promptText":
        "Great — Kotlin it is. Now, how do we build the screens that users will "
        "see? What are my options and what do you recommend for a beginner?",
    "rationale_en":
        "Same trick as the last step: ask for options + recommendation. The AI's "
        "job is to weigh trade-offs for you so you can decide.",
    "rationale_vn":
        "Cùng mẹo như bước trước: hỏi tùy chọn + đề xuất. Việc của AI là cân "
        "đánh đổi để bạn quyết định.",
    "ai_en":
        "Two ways:\n\n"
        "(1) **XML layouts** — the old way. You write a separate file (XML) that "
        "describes how the screen looks, then connect it to your Kotlin code. "
        "Two languages, two files per screen.\n\n"
        "(2) **Jetpack Compose** — Google's new way (since 2021). You describe "
        "screens INSIDE your Kotlin code, using regular function calls. One "
        "language. Live preview as you type.\n\n"
        "I strongly recommend **Compose**:\n"
        "• Less to learn (just Kotlin, no XML)\n"
        "• ~30% less code than XML\n"
        "• It's the future — Google switched all their new apps to it.\n\n"
        "Ready to write your first screen?",
    "ai_vn":
        "Hai cách:\n\n"
        "(1) **XML layouts** — cách cũ. Bạn viết file XML riêng mô tả màn hình, "
        "rồi nối với code Kotlin. Hai ngôn ngữ, hai file mỗi màn hình.\n\n"
        "(2) **Jetpack Compose** — cách mới của Google (từ 2021). Mô tả màn hình "
        "NGAY TRONG code Kotlin, gọi function thông thường. Một ngôn ngữ. Live "
        "preview khi gõ.\n\n"
        "Tôi đề xuất **Compose**:\n"
        "• Ít phải học hơn (chỉ Kotlin, bỏ XML)\n"
        "• Ít code hơn ~30% so với XML\n"
        "• Là tương lai — Google đã chuyển tất cả app mới sang.\n\n"
        "Sẵn sàng viết màn hình đầu tiên chưa?",
    "clawd_en":
        "Same recipe — you asked, AI weighed options, AI recommended with reasons, "
        "YOU decided. You now have a plan: Android + `Kotlin` + `Compose`. You "
        "don't need to know what those are technically. You know WHY they were "
        "chosen. Next: writing the actual first piece of code.",
    "clawd_vn":
        "Cùng công thức — bạn hỏi, AI cân nhắc, AI đề xuất có lý do, BẠN quyết. "
        "Giờ bạn có kế hoạch: Android + `Kotlin` + `Compose`. Bạn không cần biết "
        "kỹ thuật chúng là gì. Bạn biết VÌ SAO chọn. Tiếp: viết miếng code đầu tiên.",
},

# ===================== STEP 4: FIRST CODE — MAIN SCREEN =====================
4: {
    "title_en": "Make the first screen",
    "title_vn": "Tạo màn hình đầu tiên",
    "instruction_en":
        "Now we tell the AI exactly what the first screen should look like, in "
        "plain English. The AI writes the code. Don't worry about reading "
        "every line — the line-by-line notes on the right will explain each "
        "piece as it appears.",
    "instruction_vn":
        "Giờ ta nói AI chính xác màn hình đầu tiên trông thế nào, bằng tiếng Anh "
        "thường. AI viết code. Đừng lo đọc từng dòng — chú thích bên phải sẽ "
        "giải thích từng phần khi nó xuất hiện.",
    "promptText":
        "Let's build the main screen. It should have:\n"
        "1. A big title at the top saying 'Rain Alert Vietnam'\n"
        "2. A short subtitle saying 'Tap the button if you see rain'\n"
        "3. One large orange button below saying \"It's Raining Here\"\n\n"
        "Write the Kotlin Compose code and explain every single line. Pretend "
        "I've never seen code before.",
    "rationale_en":
        "Be specific. Title text? Yes. Subtitle? Yes. Colour? Yes. Asking the AI "
        "to explain EVERY LINE turns a code dump into a lesson.",
    "rationale_vn":
        "Cụ thể. Tiêu đề? Có. Phụ đề? Có. Màu? Có. Nhờ AI giải thích MỖI DÒNG "
        "biến code thành bài học.",
    "ai_en":
        "Here's your main screen. A few words to know:\n\n"
        "• `@Composable` — a tag that says 'this function draws part of a screen'\n"
        "• `fun` — short for 'function', a reusable block of code\n"
        "• `Text(...)` — draws text on screen\n"
        "• `Button(...)` — draws a tappable button\n"
        "• `Column` — stacks things on top of each other vertically\n"
        "• `Modifier` — controls size, padding, colour, like CSS for the web\n\n"
        "Read it once, then look at the line notes on the right to see what "
        "each line does. Don't try to memorise it — you'll never have to type "
        "this by hand.",
    "ai_vn":
        "Đây là màn hình chính của bạn. Vài từ cần biết:\n\n"
        "• `@Composable` — nhãn nói \"function này vẽ một phần màn hình\"\n"
        "• `fun` — viết tắt của 'function', khối code tái sử dụng được\n"
        "• `Text(...)` — vẽ chữ trên màn hình\n"
        "• `Button(...)` — vẽ nút bấm được\n"
        "• `Column` — xếp các thứ chồng dọc lên nhau\n"
        "• `Modifier` — điều chỉnh kích thước, padding, màu — như CSS web\n\n"
        "Đọc một lần, rồi xem chú thích dòng bên phải. Đừng cố thuộc — bạn sẽ "
        "không bao giờ phải tự gõ lại.",
    "clawd_en":
        "🎉 You just wrote your first code — well, the AI did, you described it. "
        "Each line has one job. `@Composable` is a stamp saying \"this is a "
        "screen part.\" `Text()` and `Button()` are Lego blocks — call them, "
        "they appear. You're not memorising. You're learning to READ. Next: "
        "how the app remembers things — variables.",
    "clawd_vn":
        "🎉 Bạn vừa viết code đầu tiên — đúng ra là AI viết, bạn mô tả. Mỗi dòng "
        "có một việc. `@Composable` là con dấu nói \"đây là một phần màn hình.\" "
        "`Text()` và `Button()` là viên Lego — gọi, chúng xuất hiện. Bạn không "
        "thuộc lòng. Bạn học cách ĐỌC. Tiếp: app ghi nhớ thông tin thế nào — variables.",
},

# ====================== STEP 5: VARIABLES (val / var) =======================
5: {
    "title_en": "How the app remembers things — variables",
    "title_vn": "Cách app ghi nhớ — variables",
    "instruction_en":
        "An app constantly needs to remember things — the time of a rain report, "
        "whether the user tapped the button, how many reports exist. We store "
        "information in VARIABLES. Two kinds in Kotlin:\n\n"
        "• `val` — a sticky note you write once and never change\n"
        "• `var` — a sticky note where you can cross out the old value and "
        "write a new one",
    "instruction_vn":
        "App liên tục cần nhớ — thời gian báo mưa, user đã nhấn nút chưa, có "
        "bao nhiêu báo cáo. Ta lưu thông tin vào VARIABLES. Hai loại trong Kotlin:\n\n"
        "• `val` — tờ giấy ghi chú viết một lần, không sửa\n"
        "• `var` — tờ giấy có thể gạch giá trị cũ và viết giá trị mới",
    "promptText":
        "I want the app to remember two things:\n"
        "1. The exact time when the user tapped the report button — this won't "
        "change after it's set.\n"
        "2. Whether the user has already reported rain today — this starts as "
        "false, becomes true when they tap.\n\n"
        "Show me the Kotlin code, and please explain the difference between "
        "`val` and `var` like I'm five.",
    "rationale_en":
        "Describe both things you want to store AND ask the AI to explain the "
        "underlying concept. \"Explain like I'm five\" is a legit prompt — it "
        "tells the AI to drop the jargon.",
    "rationale_vn":
        "Mô tả cả hai thứ cần lưu VÀ nhờ AI giải thích khái niệm. \"Giải thích "
        "như tôi 5 tuổi\" là prompt hợp lệ — bảo AI bỏ thuật ngữ.",
    "ai_en":
        "Sticky notes!\n\n"
        "Imagine your fridge. You stick two notes on it:\n"
        "• Note A: \"My birthday: March 14\" — you'll never change this. That's "
        "a `val`. Write once, read forever.\n"
        "• Note B: \"My current mood: 😐\" — you might cross it out tomorrow "
        "and write \"😊\". That's a `var`. Can be rewritten.\n\n"
        "For your app:\n"
        "• `val reportTime = System.currentTimeMillis()` → the moment they "
        "tapped, frozen forever.\n"
        "• `var hasReported = false` → starts false, becomes true on tap.\n\n"
        "Rule of thumb: use `val` everywhere you can. Only use `var` if you "
        "REALLY need to change the value later. Less change = fewer bugs.",
    "ai_vn":
        "Tờ giấy ghi chú!\n\n"
        "Tưởng tượng tủ lạnh. Bạn dán hai tờ giấy:\n"
        "• Giấy A: \"Sinh nhật: 14/3\" — không bao giờ đổi. Đó là `val`. Viết "
        "một lần, đọc mãi.\n"
        "• Giấy B: \"Tâm trạng hiện tại: 😐\" — mai bạn có thể gạch và viết "
        "\"😊\". Đó là `var`. Đổi được.\n\n"
        "Cho app:\n"
        "• `val reportTime = System.currentTimeMillis()` → khoảnh khắc họ nhấn, "
        "đóng băng mãi mãi.\n"
        "• `var hasReported = false` → bắt đầu false, thành true khi nhấn.\n\n"
        "Quy tắc: dùng `val` ở đâu được thì dùng. Chỉ dùng `var` khi THẬT SỰ "
        "cần đổi sau. Ít đổi = ít bug.",
    "clawd_en":
        "Variables are the app's memory. `val` = write once. `var` = re-writable. "
        "That's it. Real developers use 80% `val` because most things shouldn't "
        "change. Less change = fewer bugs. Next: a weird one — what happens "
        "when info just isn't there yet? The dreaded `null`.",
    "clawd_vn":
        "Variables = bộ nhớ của app. `val` = viết một lần. `var` = sửa được. "
        "Hết. Dev thật dùng 80% là `val` vì hầu hết thứ không nên đổi. Ít đổi "
        "= ít bug. Tiếp: kỳ lạ — chuyện gì xảy ra khi chưa có thông tin? `null` đáng sợ.",
},

# ========================= STEP 6: NULL & SAFE-CALL =========================
6: {
    "title_en": "What if there's nothing? — null",
    "title_vn": "Nếu chưa có gì thì sao? — null",
    "instruction_en":
        "Sometimes the app expects information but it isn't there yet. Example: "
        "the user's GPS location — until they grant permission AND the satellite "
        "fix happens, there's no location. In code, this \"nothing\" is called "
        "`null`. Think of it as an empty box.\n\n"
        "THE DANGER: if the app tries to USE an empty box (like 'read the "
        "latitude from this empty location'), it crashes. Kotlin has special "
        "punctuation (`?`) to handle this safely.",
    "instruction_vn":
        "Đôi khi app cần thông tin nhưng chưa có. Ví dụ: vị trí GPS — trước khi "
        "user cấp quyền VÀ vệ tinh khóa được vị trí, không có dữ liệu. Trong "
        "code, \"không có gì\" gọi là `null`. Hãy nghĩ như cái hộp rỗng.\n\n"
        "NGUY HIỂM: nếu app cố MỞ hộp rỗng (như \"đọc kinh độ từ vị trí rỗng "
        "này\"), app crash. Kotlin có ký hiệu đặc biệt (`?`) xử lý an toàn.",
    "promptText":
        "Sometimes the user's location won't be available yet — maybe they "
        "haven't granted permission, or GPS hasn't found them. How do I store "
        "the location safely so the app doesn't crash if it's not there yet? "
        "Explain what `null` means and the safe way to handle it.",
    "rationale_en":
        "Describe the REAL scenario (\"location not available yet\") and ask "
        "for safety. The AI introduces `null` and `?` naturally because they "
        "answer your scenario.",
    "rationale_vn":
        "Mô tả tình huống THẬT (\"vị trí chưa có\") và hỏi cách an toàn. AI "
        "giới thiệu `null` và `?` tự nhiên vì đó là câu trả lời.",
    "ai_en":
        "`null` literally means \"nothing here.\" An empty box.\n\n"
        "In Kotlin, if you declare a variable as `Location`, it MUST have a "
        "value — Kotlin guarantees it. But if you write `Location?` (with the "
        "question mark), you're saying \"this might be empty.\"\n\n"
        "Then, before you USE the value, you check:\n"
        "• `currentLocation?.latitude` — the `?.` is a SAFE CALL. It means "
        "\"if it's not null, get the latitude. If it IS null, give me null "
        "back instead of crashing.\"\n"
        "• `if (currentLocation != null) { … }` — old-school explicit check.\n\n"
        "Both work. `?.` is shorter and what most Kotlin code uses today.",
    "ai_vn":
        "`null` nghĩa đen là \"không có gì.\" Cái hộp rỗng.\n\n"
        "Trong Kotlin, nếu khai biến là `Location`, nó BUỘC PHẢI có giá trị. "
        "Nhưng nếu viết `Location?` (có dấu hỏi), bạn nói \"có thể rỗng.\"\n\n"
        "Rồi trước khi DÙNG, kiểm tra:\n"
        "• `currentLocation?.latitude` — `?.` là SAFE CALL. Nghĩa là \"nếu khác "
        "null, lấy latitude. Nếu LÀ null, trả null thay vì crash.\"\n"
        "• `if (currentLocation != null) { … }` — kiểm tra rõ ràng kiểu cũ.\n\n"
        "Cả hai đều hoạt động. `?.` ngắn gọn và hầu hết Kotlin hiện đại dùng.",
    "clawd_en":
        "`null` = empty box. The `?` after a type is Kotlin saying \"I know this "
        "might be empty, I'll be careful.\" This is literally the reason Kotlin "
        "exists — `Java` didn't have it, and that caused billions of crashes. "
        "You just dodged the most common bug in app history. Next: how the app "
        "politely asks the phone for things (permissions).",
    "clawd_vn":
        "`null` = hộp rỗng. Dấu `?` sau type là Kotlin nói \"tôi biết có thể "
        "rỗng, tôi sẽ cẩn thận.\" Đây CHÍNH LÀ lý do Kotlin tồn tại — `Java` "
        "không có, gây hàng tỷ crash. Bạn vừa né được bug phổ biến nhất lịch "
        "sử app. Tiếp: app xin quyền điện thoại lịch sự (permissions).",
},

# ============================ STEP 7: PERMISSIONS ===========================
7: {
    "title_en": "Politely asking for permission",
    "title_vn": "Lịch sự xin quyền",
    "instruction_en":
        "An app can't just grab the user's location, camera, or microphone. It "
        "has to ASK, and the user can say yes or no. This needs two things:\n\n"
        "(1) The app DECLARES upfront what it might want (in a special file "
        "called AndroidManifest.xml — like a permission slip you hand the "
        "phone when installing).\n"
        "(2) At the right moment, the app POPS UP a dialog asking the user.\n\n"
        "Forget either half and the app crashes or silently fails.",
    "instruction_vn":
        "App không thể tự lấy vị trí, camera, micro. Phải XIN, user trả lời "
        "đồng ý/từ chối. Cần hai thứ:\n\n"
        "(1) App KHAI BÁO trước những gì có thể cần (trong file đặc biệt tên "
        "AndroidManifest.xml — như tờ đơn xin gửi điện thoại khi cài).\n"
        "(2) Đúng lúc, app HIỆN popup hỏi user.\n\n"
        "Quên nửa nào cũng làm app crash hoặc lỗi im lặng.",
    "promptText":
        "Add code so the app can use the user's location. Show me BOTH parts: "
        "the manifest declaration AND the popup that asks the user the first "
        "time. Explain why both are needed.",
    "rationale_en":
        "We explicitly ask for BOTH halves. If you only ask for one, the AI "
        "might give you only one. Specificity beats wishful thinking.",
    "rationale_vn":
        "Ta xin RÕ RÀNG cả hai nửa. Nếu chỉ xin một, AI có thể chỉ cho một. "
        "Cụ thể thắng mong đợi.",
    "ai_en":
        "Two halves, like you said:\n\n"
        "(1) **AndroidManifest.xml** — add `<uses-permission android:name=\""
        "android.permission.ACCESS_FINE_LOCATION\"/>`. Think of this as the "
        "app saying \"hi, I may want fine location at some point.\" Without "
        "this, the phone outright refuses, no matter what the user clicks.\n\n"
        "(2) **Runtime request** — modern Android (6.0+) requires you to "
        "actually ask the user in a popup the FIRST time you need it. They "
        "tap Allow or Deny. If they deny, your app must handle it gracefully "
        "(don't crash, show a friendly message).\n\n"
        "Why both? The manifest is the legal notice. The popup is the user's "
        "choice. The OS enforces both.",
    "ai_vn":
        "Hai nửa, đúng như bạn nói:\n\n"
        "(1) **AndroidManifest.xml** — thêm `<uses-permission android:name=\""
        "android.permission.ACCESS_FINE_LOCATION\"/>`. Nghĩ như app nói \"chào, "
        "có thể tôi sẽ cần vị trí chính xác.\" Không có dòng này, điện thoại "
        "từ chối thẳng, dù user click gì.\n\n"
        "(2) **Runtime request** — Android hiện đại (6.0+) yêu cầu thật sự hỏi "
        "user qua popup LẦN ĐẦU cần. Họ chọn Allow hay Deny. Nếu từ chối, app "
        "phải xử lý nhẹ nhàng (không crash, hiện thông báo thân thiện).\n\n"
        "Vì sao cả hai? Manifest là thông báo pháp lý. Popup là lựa chọn của user. "
        "Hệ điều hành ép cả hai.",
    "clawd_en":
        "Two-step ritual: DECLARE in manifest (legal notice) + ASK the user "
        "(politeness). Skip the manifest → security crash. Skip the popup → "
        "user never agrees. Real apps always do both. Next: how the app makes "
        "decisions — `if` / `else`.",
    "clawd_vn":
        "Hai bước: KHAI BÁO trong manifest (pháp lý) + HỎI user (lịch sự). Bỏ "
        "manifest → security crash. Bỏ popup → user không đồng ý. App thật "
        "luôn làm cả hai. Tiếp: app ra quyết định — `if` / `else`.",
},

# =========================== STEP 8: IF / ELSE ==============================
8: {
    "title_en": "Making decisions in code — if / else",
    "title_vn": "Đưa ra quyết định trong code — if / else",
    "instruction_en":
        "Apps constantly make decisions. \"IF the user granted location, do "
        "X. OTHERWISE, do Y.\" In code, this is `if` and `else`. It reads "
        "like English. The condition goes in (parentheses). The action goes "
        "in {curly braces}.",
    "instruction_vn":
        "App liên tục ra quyết định. \"NẾU user cho phép vị trí, làm X. NGƯỢC "
        "LẠI, làm Y.\" Trong code, đó là `if` và `else`. Đọc như tiếng Anh. "
        "Điều kiện trong (ngoặc đơn). Hành động trong {ngoặc nhọn}.",
    "promptText":
        "If the user granted location permission, save their current GPS "
        "position so we can send a rain report. If they denied permission, "
        "show a text box asking them to type their neighbourhood name "
        "manually. Write this in Kotlin and explain how `if`/`else` works.",
    "rationale_en":
        "Describe BOTH paths the app might take. The AI's `if/else` block "
        "matches your description one-to-one — that's why describing both "
        "halves matters.",
    "rationale_vn":
        "Mô tả CẢ HAI đường app có thể đi. Khối `if/else` của AI khớp mô tả "
        "1-1 — đó là lý do mô tả đủ cả hai nửa quan trọng.",
    "ai_en":
        "Code = a recipe with forks. Here's the pattern:\n\n"
        "```\n"
        "if (someCondition) {\n"
        "    // runs when condition is TRUE\n"
        "} else {\n"
        "    // runs when condition is FALSE\n"
        "}\n"
        "```\n\n"
        "For your case: `if (hasLocationPermission) { saveGpsPosition() } else "
        "{ showManualEntry() }`. One branch runs, the other doesn't. Never "
        "both. The app picks based on what's true RIGHT NOW.\n\n"
        "You can chain them: `if … else if … else if … else`. But usually two "
        "branches is enough.",
    "ai_vn":
        "Code = công thức nấu ăn có ngã rẽ. Khuôn mẫu:\n\n"
        "```\n"
        "if (điềuKiện) {\n"
        "    // chạy khi điều kiện ĐÚNG\n"
        "} else {\n"
        "    // chạy khi điều kiện SAI\n"
        "}\n"
        "```\n\n"
        "Trường hợp bạn: `if (hasLocationPermission) { saveGpsPosition() } else "
        "{ showManualEntry() }`. Một nhánh chạy, nhánh kia không. Không bao "
        "giờ cả hai. App chọn dựa trên cái gì đúng NGAY BÂY GIỜ.\n\n"
        "Bạn có thể nối: `if … else if … else if … else`. Nhưng thường hai "
        "nhánh là đủ.",
    "clawd_en":
        "Code = a recipe with forks. `if` runs one branch, `else` runs the "
        "other. You just thought through BOTH paths your app might take — "
        "that's what real developers do BEFORE writing code. Plan the "
        "branches, then write. Next: where shared data lives — the cloud.",
    "clawd_vn":
        "Code = công thức có ngã rẽ. `if` chạy một nhánh, `else` chạy nhánh "
        "kia. Bạn vừa nghĩ qua CẢ HAI hướng app có thể đi — dev thật làm thế "
        "TRƯỚC khi viết code. Lập kế nhánh, rồi viết. Tiếp: data chung sống ở "
        "đâu — trên mây.",
},

# ============================= STEP 9: FIREBASE =============================
9: {
    "title_en": "Sharing data in the cloud — Firebase",
    "title_vn": "Chia sẻ data trên mây — Firebase",
    "instruction_en":
        "When one user reports rain, ALL users need to see it. The data can't "
        "live on just one phone — it has to live somewhere everyone can read.\n\n"
        "That place is called a CLOUD DATABASE. We'll use Firebase, Google's "
        "free one. Think of it as a shared notebook on the internet — every "
        "phone writes to and reads from the same notebook.",
    "instruction_vn":
        "Khi một user báo mưa, TẤT CẢ user cần thấy. Data không thể chỉ sống "
        "trên một điện thoại — phải ở nơi mọi người đọc được.\n\n"
        "Nơi đó gọi là CLOUD DATABASE. Ta dùng Firebase — của Google, miễn phí. "
        "Nghĩ như cuốn sổ tay chung trên internet — mọi điện thoại viết và đọc "
        "cùng một cuốn sổ.",
    "promptText":
        "Set up Firebase Realtime Database for this app. When a user taps the "
        "rain button, send their location and the timestamp to Firebase. "
        "Walk me through the full setup, including the website steps and the "
        "Kotlin code. Explain what Firebase is in simple terms.",
    "rationale_en":
        "Firebase has setup steps OUTSIDE the code (creating a project on "
        "the Firebase website, downloading a config file). Asking the AI for "
        "the FULL walkthrough including the website steps prevents the \"I "
        "did the code but it doesn't work\" trap.",
    "rationale_vn":
        "Firebase có bước setup NGOÀI code (tạo project trên web Firebase, tải "
        "file cấu hình). Nhờ AI hướng dẫn TOÀN BỘ kể cả bước trên web giúp "
        "tránh bẫy \"code xong mà không chạy.\"",
    "ai_en":
        "Firebase = a free database hosted by Google. Picture a shared Google "
        "Doc that any app can read/write to in real time. Setup:\n\n"
        "(1) Go to console.firebase.google.com → create a new project.\n"
        "(2) Add an Android app to the project → enter your app's package "
        "name → download the `google-services.json` file → drop it in your "
        "`app/` folder.\n"
        "(3) Add Firebase lines to your `build.gradle` files (the AI will "
        "give you these).\n"
        "(4) In your Kotlin code, call `FirebaseDatabase.getInstance()."
        "getReference(\"reports\").push().setValue(yourReport)`. Done — that "
        "one line sends data to the cloud.\n\n"
        "Reads work the same: `.addValueEventListener { … }` and Firebase "
        "calls you back whenever new data arrives.",
    "ai_vn":
        "Firebase = database miễn phí của Google. Hãy hình dung Google Doc "
        "chung mà bất kỳ app nào cũng đọc/ghi real-time. Setup:\n\n"
        "(1) Vào console.firebase.google.com → tạo project mới.\n"
        "(2) Thêm app Android → nhập package name → tải file `google-services."
        "json` → đặt vào thư mục `app/`.\n"
        "(3) Thêm dòng Firebase vào file `build.gradle` (AI sẽ đưa).\n"
        "(4) Trong code Kotlin: `FirebaseDatabase.getInstance().getReference("
        "\"reports\").push().setValue(yourReport)`. Xong — một dòng gửi data lên mây.\n\n"
        "Đọc tương tự: `.addValueEventListener { … }`, Firebase gọi lại bạn "
        "mỗi khi có data mới.",
    "clawd_en":
        "Firebase = shared notebook in the cloud. Every user reads from and "
        "writes to the same notebook. This is how every app you use works: "
        "`WhatsApp` messages, `Instagram` photos, `Spotify` playlists — all "
        "in similar cloud databases, not on your phone. Next: how the app "
        "handles MANY rain reports at once — lists.",
    "clawd_vn":
        "Firebase = sổ tay chung trên mây. Mỗi user đọc và viết cùng sổ. Mọi "
        "app bạn dùng đều thế: tin nhắn `WhatsApp`, ảnh `Instagram`, playlist "
        "`Spotify` — đều ở cloud database tương tự, không nằm trên điện thoại "
        "bạn. Tiếp: app xử lý NHIỀU báo cáo mưa cùng lúc — lists.",
},

# ============================= STEP 10: LISTS ===============================
10: {
    "title_en": "Working with many items — lists",
    "title_vn": "Làm việc với nhiều thứ — lists",
    "instruction_en":
        "When 50 users have reported rain, the app holds 50 reports. We store "
        "collections of things in a LIST. A list is exactly what it sounds "
        "like — items in order. In Kotlin we write `List<RainReport>` which "
        "reads as \"a list of rain reports.\"",
    "instruction_vn":
        "Khi 50 user đã báo mưa, app giữ 50 báo cáo. Ta lưu nhóm thứ trong "
        "LIST. List đúng như tên gọi — các mục theo thứ tự. Trong Kotlin viết "
        "`List<RainReport>` đọc là \"một list các báo cáo mưa.\"",
    "promptText":
        "Now that reports stream in from Firebase, store them in a list and "
        "show the 10 most recent on the screen. Each row should show the "
        "location name and how long ago it was reported (e.g. \"5 minutes "
        "ago\"). Use a scrollable view.",
    "rationale_en":
        "Specifying the data shape (10 most recent), the row contents (location "
        "+ relative time), and the behaviour (scrollable) means the AI builds "
        "exactly what you pictured, not its best guess.",
    "rationale_vn":
        "Chỉ định hình dạng data (10 mới nhất), nội dung hàng (vị trí + thời "
        "gian tương đối), hành vi (cuộn) khiến AI xây đúng thứ bạn hình dung, "
        "không phải đoán mò.",
    "ai_en":
        "Lists in Kotlin: `List<RainReport>`. The angle brackets say \"a list "
        "of THIS type.\" Examples:\n\n"
        "• `List<String>` — a list of strings (words)\n"
        "• `List<Int>` — a list of integers (whole numbers)\n"
        "• `List<RainReport>` — a list of rain reports\n\n"
        "To show a long scrollable list in Compose, we use `LazyColumn`. Why "
        "\"lazy\"? Because if you have 10,000 items, it only draws the ones "
        "actually on screen right now — much faster. Inside, you write `items"
        "(reports) { report → … }` and the function runs once per report to "
        "draw a row.",
    "ai_vn":
        "List trong Kotlin: `List<RainReport>`. Dấu ngoặc nhọn nói \"list "
        "kiểu NÀY.\" Ví dụ:\n\n"
        "• `List<String>` — list các chuỗi (từ)\n"
        "• `List<Int>` — list số nguyên\n"
        "• `List<RainReport>` — list báo cáo mưa\n\n"
        "Hiện list cuộn dài trong Compose dùng `LazyColumn`. Vì sao \"lazy\"? "
        "Vì với 10.000 mục, nó chỉ vẽ những mục đang trên màn hình — nhanh "
        "hơn. Bên trong, viết `items(reports) { report → … }` và function "
        "chạy một lần mỗi report để vẽ một hàng.",
    "clawd_en":
        "Lists hold many things at once. `List<RainReport>` = \"a list of rain "
        "reports.\" Almost every screen you've ever scrolled — `Instagram` "
        "feed, `WhatsApp` chats, contacts — is a `LazyColumn` (or its iOS "
        "cousin). Same pattern everywhere. Next: instead of a boring list, "
        "let's put the reports on a real map.",
    "clawd_vn":
        "List chứa nhiều thứ cùng lúc. `List<RainReport>` = \"list các báo cáo "
        "mưa.\" Gần như mọi màn hình bạn từng cuộn — feed `Instagram`, chat "
        "`WhatsApp`, danh bạ — đều là `LazyColumn` (hoặc anh em iOS). Cùng "
        "khuôn mẫu khắp nơi. Tiếp: thay vì list nhàm, đặt báo cáo lên map thật.",
},

# ============================== STEP 11: MAP ================================
11: {
    "title_en": "Putting reports on a map",
    "title_vn": "Đặt báo cáo lên bản đồ",
    "instruction_en":
        "Coordinates by themselves are boring. Let's show each rain report as a "
        "pin on Google Maps. Users immediately see WHERE rain is being reported "
        "around them. Same data we already have — different way of showing it.",
    "instruction_vn":
        "Toạ độ trơ thì nhàm. Hiện mỗi báo cáo mưa như cây ghim trên Google "
        "Maps. User thấy ngay CHỖ NÀO đang được báo mưa quanh họ. Cùng data — "
        "cách hiện khác.",
    "promptText":
        "Replace the list view with a Google Map. Show each rain report as a "
        "blue pin at its latitude/longitude. When the user taps a pin, show a "
        "small popup with the time of the report (e.g. \"reported 12 minutes "
        "ago\"). Centre the map on the user's current location.",
    "rationale_en":
        "Specify pin colour (blue), interaction (tap → popup with time), and "
        "the initial map position (user's location). Three concrete details = "
        "three things the AI won't guess wrong.",
    "rationale_vn":
        "Chỉ định màu ghim (xanh), tương tác (chạm → popup thời gian), vị trí "
        "ban đầu (vị trí user). Ba chi tiết cụ thể = ba thứ AI không đoán sai.",
    "ai_en":
        "Google Maps inside Compose uses the `GoogleMap` composable from the "
        "Maps SDK. For each report, you drop a `Marker` at its lat/lng. "
        "Markers have an `onClick` parameter — point it at a function that "
        "shows your popup.\n\n"
        "Setup: like Firebase, Maps needs a website step — go to Google Cloud "
        "Console, enable the Maps SDK, get an API key, paste it in your "
        "manifest. The AI will paste a copy-ready snippet for each step.",
    "ai_vn":
        "Google Maps trong Compose dùng `GoogleMap` composable từ Maps SDK. "
        "Mỗi báo cáo, thả `Marker` ở lat/lng. Marker có `onClick` — trỏ vào "
        "function hiện popup.\n\n"
        "Setup: như Firebase, Maps cần bước trên web — vào Google Cloud "
        "Console, bật Maps SDK, lấy API key, dán vào manifest. AI sẽ dán "
        "snippet sẵn sàng copy cho mỗi bước.",
    "clawd_en":
        "Same recipe — describe in plain English, AI translates to code. The "
        "map is just a fancier display of the same list. Data lives in "
        "Firebase. The screen just READS and shows it. Next: how to buzz "
        "OTHER users' phones when rain is reported nearby — push notifications.",
    "clawd_vn":
        "Cùng công thức — mô tả tiếng Anh thường, AI dịch ra code. Bản đồ chỉ "
        "là cách hiện bóng bẩy hơn của cùng list. Data ở Firebase. Màn hình "
        "chỉ ĐỌC và hiện. Tiếp: cách ping điện thoại NGƯỜI KHÁC khi có mưa "
        "gần đó — push notifications.",
},

# ========================== STEP 12: NOTIFICATIONS ==========================
12: {
    "title_en": "Alerting nearby users — push notifications",
    "title_vn": "Cảnh báo user gần đó — push notifications",
    "instruction_en":
        "When one user reports rain, we want OTHER phones nearby to buzz with "
        "an alert — even if their app is closed. This is a PUSH NOTIFICATION. "
        "We'll use Firebase Cloud Messaging (FCM) — Google's free notification "
        "postman service.",
    "instruction_vn":
        "Khi một user báo mưa, ta muốn điện thoại NGƯỜI KHÁC gần đó kêu cảnh "
        "báo — dù app đóng. Đó là PUSH NOTIFICATION. Ta dùng Firebase Cloud "
        "Messaging (FCM) — dịch vụ đưa thư thông báo miễn phí của Google.",
    "promptText":
        "When a user taps the rain button, send a push notification using "
        "Firebase Cloud Messaging to all users within 5km of that location. "
        "The notification title should be \"Rain reported nearby ☔\" and the "
        "body should say something like \"Someone reported rain 2km away\". "
        "Tapping the notification should open the map view. Walk me through "
        "the setup.",
    "rationale_en":
        "Specify the trigger (rain button tap), the audience (within 5km), the "
        "exact title and body text, and the tap behaviour. The clearer you "
        "are, the less the AI has to guess.",
    "rationale_vn":
        "Chỉ định kích hoạt (nhấn nút mưa), đối tượng (trong 5km), chính xác "
        "tiêu đề và nội dung, hành vi khi chạm. Rõ ràng hơn → AI ít đoán hơn.",
    "ai_en":
        "FCM = Google's free postman. It can deliver tiny messages to phones "
        "even when your app is closed. Pipeline:\n\n"
        "(1) When a new rain report is written to Firebase, a tiny server "
        "called a \"Cloud Function\" wakes up.\n"
        "(2) It looks at all users (also stored in Firebase) and finds the "
        "ones within 5km of the new report.\n"
        "(3) It sends each of them an FCM message.\n"
        "(4) The phones receive it, show the notification, and on tap open the "
        "map screen.\n\n"
        "You'll write the Cloud Function in JavaScript (Firebase makes you), "
        "but it's only ~15 lines. The AI will write it.",
    "ai_vn":
        "FCM = bưu tá miễn phí của Google. Gửi tin nhỏ tới điện thoại kể cả "
        "khi app đóng. Pipeline:\n\n"
        "(1) Khi báo cáo mưa mới ghi vào Firebase, một server nhỏ tên \"Cloud "
        "Function\" thức dậy.\n"
        "(2) Nó xem tất cả user (cũng lưu trong Firebase) và tìm những người "
        "trong 5km quanh báo cáo mới.\n"
        "(3) Gửi mỗi người một tin FCM.\n"
        "(4) Điện thoại nhận, hiện thông báo, chạm vào mở màn hình bản đồ.\n\n"
        "Bạn sẽ viết Cloud Function bằng JavaScript (Firebase bắt thế), nhưng "
        "chỉ ~15 dòng. AI sẽ viết.",
    "clawd_en":
        "Push notifications = the postman. Even with your app shut, `FCM` "
        "delivers. This is what makes `WhatsApp` and `Messenger` feel alive. "
        "Notice you didn't write one line of networking code — Firebase "
        "handles all the plumbing. Real devs don't reinvent wheels; they ASK "
        "for the right tools. Next: your first bug. Don't panic.",
    "clawd_vn":
        "Push notification = bưu tá. Dù app đóng, `FCM` vẫn giao. Đó là thứ "
        "làm `WhatsApp` và `Messenger` cảm thấy sống động. Chú ý: bạn không "
        "viết một dòng code mạng nào — Firebase lo hết phần ống nước. Dev "
        "thật không phát minh lại bánh xe; họ HỎI công cụ đúng. Tiếp: bug đầu "
        "tiên. Đừng hoảng.",
},

# =========================== STEP 13: BUG #1 ================================
13: {
    "title_en": "Bug #1: SecurityException (missing permission)",
    "title_vn": "Bug #1: SecurityException (thiếu quyền)",
    "instruction_en":
        "Your app just crashed with a long red error called `SecurityException`. "
        "DON'T PANIC — this is normal. Real developers spend most of their time "
        "debugging.\n\n"
        "The trick: copy the ENTIRE error and paste it to the AI. The error "
        "tells you exactly what went wrong if you read it. AI reads it faster "
        "than you ever will.",
    "instruction_vn":
        "App vừa crash với lỗi đỏ dài tên `SecurityException`. ĐỪNG HOẢNG — "
        "đây là chuyện thường. Dev thật dành phần lớn thời gian debug.\n\n"
        "Mẹo: copy TOÀN BỘ lỗi, dán cho AI. Lỗi nói chính xác sai chỗ nào nếu "
        "đọc kỹ. AI đọc nhanh hơn bạn nhiều.",
    "rationale_en":
        "Paste the WHOLE crash — error type, message, every line of the stack "
        "trace. The AI reads through it all and spots the problem. Never "
        "summarise — give it everything.",
    "rationale_vn":
        "Dán TOÀN BỘ crash — loại lỗi, thông điệp, từng dòng stack trace. AI "
        "đọc hết và phát hiện vấn đề. Đừng tóm tắt — cho hết cả.",
    "ai_en":
        "Read the second line: \"permission denied for missing "
        "ACCESS_FINE_LOCATION.\" Your runtime popup might have worked, but "
        "you forgot the manifest declaration — the OTHER half of the "
        "permission ritual.\n\n"
        "Add this line to AndroidManifest.xml between the `<manifest>` tags:\n"
        "`<uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\"/>`\n\n"
        "Rebuild and try again. The crash will be gone.",
    "ai_vn":
        "Đọc dòng thứ hai: \"permission denied for missing ACCESS_FINE_LOCATION.\" "
        "Popup runtime có thể đã chạy, nhưng bạn quên khai báo trong manifest "
        "— NỬA KIA của nghi thức quyền.\n\n"
        "Thêm dòng này vào AndroidManifest.xml giữa thẻ `<manifest>`:\n"
        "`<uses-permission android:name=\"android.permission.ACCESS_FINE_LOCATION\"/>`\n\n"
        "Build lại và thử. Crash sẽ biến mất.",
    "clawd_en":
        "Crashed → copy → paste → fixed. That's the LOOP. Real developers do "
        "this dozens of times per day. The AI reads errors better than you "
        "ever will. You just have to do the copy-paste. Next bug: "
        "`NullPointerException` — the most famous crash in coding history.",
    "clawd_vn":
        "Crash → copy → dán → sửa. Đó là VÒNG LẶP. Dev thật làm thế hàng chục "
        "lần mỗi ngày. AI đọc lỗi giỏi hơn bạn nhiều. Bạn chỉ cần copy-paste. "
        "Bug tiếp: `NullPointerException` — crash nổi tiếng nhất lịch sử code.",
},

# =========================== STEP 14: BUG #2 ================================
14: {
    "title_en": "Bug #2: NullPointerException (the empty box bites back)",
    "title_vn": "Bug #2: NullPointerException (hộp rỗng cắn lại)",
    "instruction_en":
        "Another crash, this time `NullPointerException`. Remember `null` from "
        "Step 6 — the empty box? This is what happens when the app tries to "
        "OPEN one. Somewhere we forgot the safe-call `?`. Paste the error.",
    "instruction_vn":
        "Crash khác, lần này `NullPointerException`. Nhớ `null` từ Bước 6 — "
        "hộp rỗng? Đây là chuyện app cố MỞ nó. Đâu đó ta quên safe-call `?`. "
        "Dán lỗi.",
    "rationale_en":
        "Same recipe as bug #1: paste the entire crash. The stack trace tells "
        "the AI exactly which file and line had the problem.",
    "rationale_vn":
        "Cùng công thức bug #1: dán cả crash. Stack trace nói AI chính xác "
        "file và dòng nào lỗi.",
    "ai_en":
        "Reading the stack trace — `LocationManager.kt` line 47, your code "
        "tries to read `.latitude` on a `Location` that came back null (GPS "
        "fix failed). You need either:\n\n"
        "• `currentLocation?.latitude` — the safe-call from Step 6. Returns "
        "null gracefully if the location is null.\n"
        "• Or an explicit check: `if (currentLocation != null) { … }` before "
        "reading.\n\n"
        "Either works. The safe-call is shorter.",
    "ai_vn":
        "Đọc stack trace — `LocationManager.kt` dòng 47, code cố đọc "
        "`.latitude` trên một `Location` trả về null (GPS chưa khoá). Bạn cần:\n\n"
        "• `currentLocation?.latitude` — safe-call từ Bước 6. Trả null nhẹ "
        "nhàng nếu vị trí null.\n"
        "• Hoặc kiểm tra rõ: `if (currentLocation != null) { … }` trước khi đọc.\n\n"
        "Cả hai đều được. Safe-call ngắn hơn.",
    "clawd_en":
        "`NullPointerException` = the #1 most common crash in app history "
        "(literally billions of times). You met it AND fixed it. The cause "
        "matches Step 6: null = empty box. The fix is one extra character: "
        "`?`. Next bug: Android 12 changed notification rules and broke our "
        "alerts.",
    "clawd_vn":
        "`NullPointerException` = crash #1 phổ biến nhất lịch sử app (hàng tỷ "
        "lần thật sự). Bạn gặp VÀ sửa. Nguyên nhân khớp Bước 6: null = hộp "
        "rỗng. Sửa là thêm một ký tự: `?`. Bug tiếp: Android 12 đổi luật "
        "thông báo, làm cảnh báo ta hỏng.",
},

# =========================== STEP 15: BUG #3 ================================
15: {
    "title_en": "Bug #3: Notifications silent on Android 12+",
    "title_vn": "Bug #3: Thông báo im lặng trên Android 12+",
    "instruction_en":
        "The app doesn't crash, but on newer phones (Android 13+) notifications "
        "never appear. Why? Android changed the rules — now you also need to "
        "ASK for notification permission, like with location. Paste the logs.",
    "instruction_vn":
        "App không crash, nhưng trên điện thoại mới (Android 13+) thông báo "
        "không hiện. Vì sao? Android đổi luật — giờ phải XIN quyền thông báo "
        "nữa, như với vị trí. Dán log.",
    "rationale_en":
        "Not all bugs are crashes. Sometimes the app silently does the wrong "
        "thing. Reading the LOGS (the running diary of what happened) is part "
        "of the craft — paste them too.",
    "rationale_vn":
        "Không phải bug nào cũng là crash. Đôi khi app lặng lẽ làm sai. Đọc "
        "LOG (nhật ký chạy) là một phần nghề — dán log nữa.",
    "ai_en":
        "Android 13 added `POST_NOTIFICATIONS` as a new \"dangerous\" "
        "permission. Same two-half ritual as location:\n\n"
        "(1) Add `<uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\"/>` "
        "to AndroidManifest.xml.\n"
        "(2) Ask the user at runtime, same launcher pattern as Step 7.\n\n"
        "Older Android (< 13) doesn't need this — notifications were always "
        "allowed. Modern Android wants you to ask. That's why your app worked "
        "on the emulator but failed on a new phone.",
    "ai_vn":
        "Android 13 thêm `POST_NOTIFICATIONS` thành quyền \"nguy hiểm\" mới. "
        "Cùng nghi thức hai nửa như vị trí:\n\n"
        "(1) Thêm `<uses-permission android:name=\"android.permission.POST_NOTIFICATIONS\"/>` "
        "vào AndroidManifest.xml.\n"
        "(2) Hỏi user runtime, cùng khuôn mẫu launcher như Bước 7.\n\n"
        "Android cũ hơn (< 13) không cần — thông báo luôn được phép. Android "
        "mới bắt phải xin. Đó là lý do app chạy ở emulator mà fail trên máy mới.",
    "clawd_en":
        "Bugs aren't always crashes. Sometimes the app silently does the wrong "
        "thing. Reading logs is part of the craft. You've now done three bugs "
        "— more debugging than most students do in a whole semester. Real "
        "developers debug ALL DAY. Next: you wrap up your first real app.",
    "clawd_vn":
        "Bug không phải lúc nào cũng là crash. Đôi khi app lặng lẽ làm sai. "
        "Đọc log là một phần nghề. Bạn đã làm ba bug — nhiều hơn hầu hết sinh "
        "viên cả học kỳ. Dev thật debug CẢ NGÀY. Tiếp: bạn hoàn thành app "
        "thật đầu tiên.",
},

# ============================= STEP 16: FINAL ===============================
16: {
    "title_en": "🎉 You shipped a real app!",
    "title_vn": "🎉 Bạn đã ra mắt app thật!",
    "instruction_en":
        "That's it. You built a working community rain-alert app:\n\n"
        "✓ A screen with a button\n"
        "✓ Location permission and GPS\n"
        "✓ A cloud database (Firebase)\n"
        "✓ A live map of reports\n"
        "✓ Push notifications to nearby users\n"
        "✓ Three real bugs identified and fixed\n\n"
        "You did this WITHOUT learning Kotlin syntax by heart. You learned to:\n"
        "• Describe what you want clearly\n"
        "• Ask the AI for options + reasons\n"
        "• Read error messages and paste them back\n"
        "• Trust the AI for the typing, focus on the thinking\n\n"
        "Next project: a multiplayer game / a Tinder-style match app / a "
        "personal finance tracker — each one will be bigger and introduce new "
        "ideas. See you in the next lesson.",
    "instruction_vn":
        "Vậy là xong. Bạn vừa xây app cảnh báo mưa cộng đồng:\n\n"
        "✓ Màn hình có nút\n"
        "✓ Quyền vị trí và GPS\n"
        "✓ Database trên mây (Firebase)\n"
        "✓ Bản đồ trực tiếp các báo cáo\n"
        "✓ Push notification đến user gần đó\n"
        "✓ Ba bug thật được tìm và sửa\n\n"
        "Bạn làm hết mà KHÔNG cần thuộc cú pháp Kotlin. Bạn học được:\n"
        "• Mô tả thứ mình muốn rõ ràng\n"
        "• Hỏi AI tùy chọn + lý do\n"
        "• Đọc thông báo lỗi và dán lại\n"
        "• Tin AI gõ code, bạn lo phần suy nghĩ\n\n"
        "Dự án tiếp theo: game nhiều người / app hẹn hò kiểu Tinder / app "
        "quản lý tài chính cá nhân — mỗi cái sẽ lớn hơn, dạy thêm ý mới. "
        "Hẹn gặp ở bài sau.",
    "promptText": "",
    "rationale_en": "",
    "rationale_vn": "",
    "ai_en": "",
    "ai_vn": "",
    "clawd_en": "",
    "clawd_vn": "",
},

}


def apply(data):
    for step in data["steps"]:
        n = step["stepNumber"]
        if n not in STEPS:
            continue
        s = STEPS[n]
        step["title"] = {"en": s["title_en"], "vn": s["title_vn"]}
        step["instruction"] = {"en": s["instruction_en"], "vn": s["instruction_vn"]}
        if "promptText" in s:
            step["promptText"] = s["promptText"]
        step["promptRationale"] = {"en": s["rationale_en"], "vn": s["rationale_vn"]}
        step["aiResponse"] = {"en": s["ai_en"], "vn": s["ai_vn"]}
        step["clawdExplanation"] = {"en": s["clawd_en"], "vn": s["clawd_vn"]}
    return data


def main():
    data = json.loads(PATH.read_text(encoding="utf-8"))
    apply(data)
    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Rewrote {len(STEPS)} steps in {PATH.name}")


if __name__ == "__main__":
    main()
