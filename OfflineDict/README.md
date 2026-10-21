# مترجم بدون إنترنت (EN <-> AR) لأندرويد 2.3.3+

## أسهل طريقة: GitHub (بدون تثبيت شيء)
1. أنشئ مستودعًا على github.com وارفع كل محتويات هذا المجلد (بما فيه مجلد .github).
2. افتح تبويب Actions ← "Build APK" ← زر Run workflow.
3. بعد دقائق نزّل OfflineDict-apk وفيه ملف OfflineDict.apk.

## على جهازك (Linux/Mac)
مطلوب: JDK 11+ و Android command-line tools و Python 3.
    export ANDROID_HOME=~/Android/Sdk
    ./build.sh

## توسيع القاموس
ضع ملفات .tsv في مجلد data/ بصيغة:  english<TAB>arabic  (المرادفات تُفصل بـ ؛)
ثم أعد البناء. الحجم المستهدف أقل من 80MB.
مصادر مجانية: FreeDict، Wiktionary، Tatoeba.
