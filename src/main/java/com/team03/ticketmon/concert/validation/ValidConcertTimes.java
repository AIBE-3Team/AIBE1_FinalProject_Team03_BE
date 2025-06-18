package com.team03.ticketmon.concert.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 콘서트 시간 관련 복합 검증을 위한 커스텀 Validator
 * SellerConcertCreateDTO와 SellerConcertUpdateDTO 모두 지원
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidConcertTimes.ValidConcertTimesValidator.class)
@Documented
public @interface ValidConcertTimes {

	String message() default "콘서트 시간 설정이 올바르지 않습니다";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/**
	 * ValidConcertTimes 검증 로직 구현
	 * 제네릭 인터페이스를 사용하여 여러 DTO 타입 지원
	 */
	class ValidConcertTimesValidator implements ConstraintValidator<ValidConcertTimes, Object> {

		@Override
		public void initialize(ValidConcertTimes constraintAnnotation) {
			// 초기화 로직 (필요시)
		}

		@Override
		public boolean isValid(Object obj, ConstraintValidatorContext context) {
			if (obj == null) {
				return true;
			}

			// 리플렉션을 사용하여 필드 값 추출
			ConcertTimeFields fields = extractFields(obj);

			boolean isValid = true;
			context.disableDefaultConstraintViolation();

			// 1. 공연 시간 순서 검증
			if (fields.startTime != null && fields.endTime != null) {
				if (!fields.endTime.isAfter(fields.startTime)) {
					context.buildConstraintViolationWithTemplate("종료 시간은 시작 시간보다 늦어야 합니다")
						.addPropertyNode("endTime")
						.addConstraintViolation();
					isValid = false;
				}
			}

			// 2. 예매 시간 순서 검증
			if (fields.bookingStartDate != null && fields.bookingEndDate != null) {
				if (!fields.bookingEndDate.isAfter(fields.bookingStartDate)) {
					context.buildConstraintViolationWithTemplate("예매 종료일시는 예매 시작일시보다 늦어야 합니다")
						.addPropertyNode("bookingEndDate")
						.addConstraintViolation();
					isValid = false;
				}
			}

			// 3. 예매 종료일시는 공연 시작 전이어야 함
			if (fields.bookingEndDate != null && fields.concertDate != null && fields.startTime != null) {
				LocalDateTime concertStartDateTime = fields.concertDate.atTime(fields.startTime);
				if (!fields.bookingEndDate.isBefore(concertStartDateTime)) {
					context.buildConstraintViolationWithTemplate("예매 종료일시는 공연 시작 전이어야 합니다")
						.addPropertyNode("bookingEndDate")
						.addConstraintViolation();
					isValid = false;
				}
			}

			// 4. 예매 시작일시는 공연 시작 전이어야 함
			if (fields.bookingStartDate != null && fields.concertDate != null && fields.startTime != null) {
				LocalDateTime concertStartDateTime = fields.concertDate.atTime(fields.startTime);
				if (!fields.bookingStartDate.isBefore(concertStartDateTime)) {
					context.buildConstraintViolationWithTemplate("예매 시작일시는 공연 시작 전이어야 합니다")
						.addPropertyNode("bookingStartDate")
						.addConstraintViolation();
					isValid = false;
				}
			}

			// 5. 공연 시간이 너무 길지 않은지 검증 (최대 8시간)
			if (fields.startTime != null && fields.endTime != null) {
				long hoursBetween = java.time.Duration.between(fields.startTime, fields.endTime).toHours();
				if (hoursBetween > 8) {
					context.buildConstraintViolationWithTemplate("공연 시간은 8시간을 초과할 수 없습니다")
						.addPropertyNode("endTime")
						.addConstraintViolation();
					isValid = false;
				}
			}

			// 6. 예매 기간이 너무 길지 않은지 검증 (최대 30일)
			if (fields.bookingStartDate != null && fields.bookingEndDate != null) {
				long daysBetween = java.time.Duration.between(fields.bookingStartDate, fields.bookingEndDate).toDays();
				if (daysBetween > 30) {
					context.buildConstraintViolationWithTemplate("예매 기간은 30일을 초과할 수 없습니다")
						.addPropertyNode("bookingEndDate")
						.addConstraintViolation();
					isValid = false;
				}
			}

			return isValid;
		}

		/**
		 * 리플렉션을 사용하여 DTO에서 필요한 필드들 추출
		 */
		private ConcertTimeFields extractFields(Object obj) {
			ConcertTimeFields fields = new ConcertTimeFields();

			try {
				Class<?> clazz = obj.getClass();

				// getter 메서드를 통해 필드 값 추출
				try { fields.startTime = (LocalTime) clazz.getMethod("getStartTime").invoke(obj); } catch (Exception e) { /* 무시 */ }
				try { fields.endTime = (LocalTime) clazz.getMethod("getEndTime").invoke(obj); } catch (Exception e) { /* 무시 */ }
				try { fields.concertDate = (LocalDate) clazz.getMethod("getConcertDate").invoke(obj); } catch (Exception e) { /* 무시 */ }
				try { fields.bookingStartDate = (LocalDateTime) clazz.getMethod("getBookingStartDate").invoke(obj); } catch (Exception e) { /* 무시 */ }
				try { fields.bookingEndDate = (LocalDateTime) clazz.getMethod("getBookingEndDate").invoke(obj); } catch (Exception e) { /* 무시 */ }

			} catch (Exception e) {
				// 리플렉션 실패 시 무시 (해당 필드가 없는 DTO일 수 있음)
			}

			return fields;
		}

		/**
		 * 검증에 필요한 필드들을 담는 내부 클래스
		 */
		private static class ConcertTimeFields {
			LocalTime startTime;
			LocalTime endTime;
			LocalDate concertDate;
			LocalDateTime bookingStartDate;
			LocalDateTime bookingEndDate;
		}
	}
}