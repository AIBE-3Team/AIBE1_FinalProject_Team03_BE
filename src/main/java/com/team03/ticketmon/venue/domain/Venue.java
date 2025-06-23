package com.team03.ticketmon.venue.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 공연장 정보를 나타내는 엔티티 (Venue Entity)
 * 공연장의 이름, 총 좌석 수 등(주소, ...) 정적인 마스터 데이터를 관리
 * 이 정보는 한 번 생성되면 거의 변경 안됨
 */
@Entity
@Table(name = "venues")
@Getter
@NoArgsConstructor
public class Venue {

	/**
	 * 공연장 고유 ID (Primary Key)
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "venue_id")
	private Long venueId;

	/**
	 * 공연장 이름 (예: 잠실 올림픽 주경기장)
	 */
	@Column(nullable = false, length = 100)
	private String name;

	/**
	 * 공연장 수용 인원 (총 좌석 수)
	 */
	@Column(nullable = false)
	@Min(value = 1, message = "공연장 수용 인원은 1명 이상이어야 합니다")
	private Integer capacity;

	/**
	 * 이 공연장에 속한 모든 물리적 좌석의 목록
	 */
	@OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Seat> seats;
}