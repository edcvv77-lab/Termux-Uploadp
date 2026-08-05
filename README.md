# ScanXfer Pro

مشروع Android لإرسال الملفات عبر QR وربط جلسة نقل محلية ومشفرة.

## الفكرة
- QR لا يحمل الملف.
- QR يحمل: session id + token + host + port.
- النقل يتم عبر شبكة محلية أو hotspot.
- الملفات تحفظ بنفس البايتات الأصلية.
- عند وجود أكثر من ملف، يرسل ZIP بدون تغيير المحتوى الداخلي.

## المتطلبات
- Android Studio أو Gradle + Android SDK
- JDK 17
- compileSdk 34 / minSdk 26

## البناء
```bash
./gradlew assembleDebug
```

## ملاحظات
- الجهازان يجب أن يكونا على نفس الشبكة أو hotspot.
- هذا هو الـ skeleton التنفيذي الأوضح. قد تحتاج لتعديل طفيف عند البناء على بيئة SDK فعلية.
