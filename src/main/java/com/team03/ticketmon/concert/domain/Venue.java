package com.team03.ticketmon.concert.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * Venue Entity
 * 공연장 정보 관리
 */

@Entity
@Table(name = "venues")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Venue {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "venue_id")
	private Long venueId;

	@Column(nullable = false)
	private String name;

	@OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<Seat> seats;
}
