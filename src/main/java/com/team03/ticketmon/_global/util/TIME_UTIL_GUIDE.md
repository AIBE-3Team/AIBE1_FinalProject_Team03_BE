# 🧩 TimeUtil

공통 시간 유틸리티 클래스입니다.  
현재 팀의 시간 기준은 **한국 시간(KST)** 이며,  
API 응답 포맷은 `yyyy-MM-dd`, `yyyy-MM-dd HH:mm:ss` 형식을 기본으로 사용합니다.

---

## ✅ 주요 기능

| 메서드 | 설명 |
|--------|------|
| `toKstDateString()` | KST 기준 날짜만 반환 (`yyyy-MM-dd`) |
| `toKstDateTimeString()` | KST 기준 날짜+시간 반환 (`yyyy-MM-dd HH:mm:ss`) |
| `toIso8601KstString()` | ZonedDateTime → ISO-8601 문자열 (KST 기준) |
| `fromIso8601ToKst()` | ISO-8601 문자열 → ZonedDateTime (KST 기준) |
| `toIso8601String()` | ZonedDateTime → ISO-8601 문자열 (UTC 기준) |
| `fromIso8601String()` | ISO-8601 문자열 → ZonedDateTime (UTC 기준) |

---

## 📌 팀 적용 기준

- 한국 사용자 대상 서비스이므로 **모든 시간은 KST 기준으로 처리**
- 직렬화 기준은 `application.yml` 내 설정:
  ```yaml
  spring:
    jackson:
      time-zone: Asia/Seoul
  ```
- Swagger 및 실제 응답에서도 KST 기반 문자열을 사용
- ISO-8601, UTC 포맷은 외부 연동/국제화 확장을 위한 보존 목적

---

## 🧪 테스트
- 단위 테스트는 TimeUtilTest.java에 작성되어 있음
- 주요 변환 케이스 및 시간대 차이 검증 포함
---

## 👀 예시
```java
ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

String date = TimeUtil.toKstDateString(now);
// → "2025-06-14"

String dateTime = TimeUtil.toKstDateTimeString(now);
// → "2025-06-14 00:00:00"
```
---

+ 이 문서는 공통 유틸 클래스를 설명하는 용도로 작성되었습니다.
+ 추후 `JwtUtil`, `FileUtil` 등 다른 유틸 클래스가 추가되면, 필요에 따라 이 문서에 함께 정리해주세요.