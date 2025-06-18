package com.team03.ticketmon._global.util;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ZonedDateTime ↔ 문자열 변환을 위한 공통 시간 유틸 클래스
 *
 * <p><b>✅ 팀 기본 사용 기준: 한국 시간 (KST)</b></p>
 * <ul>
 *     <li>날짜 포맷: {@code yyyy-MM-dd}</li>
 *     <li>날짜+시간 포맷: {@code yyyy-MM-dd HH:mm:ss}</li>
 *     <li>Swagger 및 실제 API 응답 시에도 이 형식을 기본으로 사용</li>
 *     <li>{@code application.yml} 설정을 통해 Jackson 직렬화 시에도 KST 기준 적용</li>
 *     <li>주 사용 메서드: {@code toKstDateString()}, {@code toKstDateTimeString()}</li>
 * </ul>
 *
 * <p><b>🌐 확장 가능성 고려 (UTC + ISO-8601)</b></p>
 * <ul>
 *     <li>국제화 또는 외부 시스템 연동(예: API 연동, 외부 DB) 시에는 ISO-8601/UTC 포맷이 요구될 수 있음</li>
 *     <li>이를 위해 {@code toIso8601String()}, {@code fromIso8601String()} 등의 메서드도 하단에 유지함</li>
 *     <li>이 확장 포맷은 선택적으로 사용 가능하며, 현재 프로젝트에서는 직접 사용하는 비율이 낮음</li>
 * </ul>
 */
public class TimeUtil {

	private static final DateTimeFormatter ISO_8601_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
	private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DATETIME_KST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

	// ===================== 🇰🇷 KST (한국 시간 우선 사용) =====================

	/**
	 * ZonedDateTime을 한국 시간(KST) 기준의 날짜 문자열로 변환합니다.
	 *
	 * @param dateTime 변환할 시간
	 * @return {@code yyyy-MM-dd} 형식의 문자열 (예: {@code "2025-06-14"})
	 */
	public static String toKstDateString(ZonedDateTime dateTime) {
		if (dateTime == null) return null;
		return dateTime.withZoneSameInstant(KST_ZONE).format(DATE_ONLY_FORMATTER);
	}

	/**
	 * ZonedDateTime을 한국 시간(KST) 기준의 날짜+시간 문자열로 변환합니다.
	 *
	 * @param dateTime 변환할 시간
	 * @return {@code yyyy-MM-dd HH:mm:ss} 형식의 문자열 (예: {@code "2025-06-14 00:00:00"})
	 */
	public static String toKstDateTimeString(ZonedDateTime dateTime) {
		if (dateTime == null) return null;
		return dateTime.withZoneSameInstant(KST_ZONE).format(DATETIME_KST_FORMATTER);
	}

	/**
	 * ZonedDateTime을 ISO-8601 문자열(KST 기준)로 변환합니다.
	 *
	 * @param dateTime 변환할 시간
	 * @return {@code ISO-8601} 포맷 문자열 (예: {@code "2025-06-14T00:00:00+09:00"})
	 */
	public static String toIso8601KstString(ZonedDateTime dateTime) {
		if (dateTime == null) return null;
		return dateTime.withZoneSameInstant(KST_ZONE).format(ISO_8601_FORMATTER);
	}

	/**
	 * ISO-8601 문자열을 한국 시간(KST) 기준의 {@code ZonedDateTime}으로 파싱합니다.
	 *
	 * @param iso8601String ISO-8601 형식의 문자열
	 * @return {@code ZonedDateTime} (KST 기준)
	 */
	public static ZonedDateTime fromIso8601ToKst(String iso8601String) {
		if (iso8601String == null || iso8601String.isBlank()) return null;
		return ZonedDateTime.parse(iso8601String, ISO_8601_FORMATTER).withZoneSameInstant(KST_ZONE);
	}

	// ===================== 🌐 UTC + ISO-8601 (확장용) =====================

	/**
	 * ZonedDateTime을 ISO-8601 문자열(UTC 기준)로 변환합니다.
	 *
	 * @param dateTime UTC 기준의 {@code ZonedDateTime}
	 * @return {@code ISO-8601} 포맷 문자열 (예: {@code "2025-06-13T15:00:00Z"})
	 */
	public static String toIso8601String(ZonedDateTime dateTime) {
		if (dateTime == null) return null;
		return dateTime.withZoneSameInstant(ZoneOffset.UTC).format(ISO_8601_FORMATTER);
	}

	/**
	 * ISO-8601 문자열을 UTC 기준의 {@code ZonedDateTime}으로 파싱합니다.
	 *
	 * @param iso8601String ISO-8601 형식의 문자열
	 * @return {@code ZonedDateTime} (UTC 기준)
	 */
	public static ZonedDateTime fromIso8601String(String iso8601String) {
		if (iso8601String == null || iso8601String.isBlank()) return null;
		return ZonedDateTime.parse(iso8601String, ISO_8601_FORMATTER).withZoneSameInstant(ZoneOffset.UTC);
	}

	// ===================== 🧪 예제 실행 (선택) =====================

	/**
	 * 콘솔에서 직접 실행하여 시간 포맷 변환 결과를 확인할 수 있는 main 메서드입니다.
	 *
	 * @param args 실행 인자 (사용하지 않음)
	 */
	public static void main(String[] args) {
		ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

		// 팀 기준 사용
		System.out.println("📅 [KST 날짜만] : " + toKstDateString(now));
		System.out.println("⏰ [KST 날짜+시간] : " + toKstDateTimeString(now));
		System.out.println("🇰🇷 [KST → ISO-8601] : " + toIso8601KstString(now));
		System.out.println("🇰🇷 [ISO-8601 → KST ZonedDateTime] : " + fromIso8601ToKst("2025-06-13T15:00:00Z"));

		// 국제화 대응용
		System.out.println("🌐 [UTC → ISO-8601] : " + toIso8601String(now));
		System.out.println("🌐 [ISO-8601 → UTC ZonedDateTime] : " + fromIso8601String("2025-06-13T15:00:00Z"));
	}
}