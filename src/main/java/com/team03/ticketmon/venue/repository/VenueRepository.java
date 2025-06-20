package com.team03.ticketmon.venue.repository;

import com.team03.ticketmon.venue.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Venue 엔티티에 대한 데이터 접근을 처리하는 Spring Data JPA 리포지토리
 */
public interface VenueRepository extends JpaRepository<Venue, Long> {}