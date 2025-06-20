-- ####################################################################
-- # Ticketmon 테스트용 초기 데이터 (Venues & Seats)
-- # 애플리케이션 실행 시 자동으로 DB에 삽입됩니다.
-- ####################################################################

-- H2 DB 등에서 기존 데이터가 있을 경우를 대비해 초기화 (선택 사항)
DELETE FROM seats;
DELETE FROM venues;
ALTER TABLE venues AUTO_INCREMENT = 1;
ALTER TABLE seats AUTO_INCREMENT = 1;


-- ====================================================================
-- 1. 공연장(Venues) 데이터 추가
-- ====================================================================
INSERT INTO venues (name, address, capacity) VALUES ('고척 스카이돔', 16744);
INSERT INTO venues (name, address, capacity) VALUES ('잠실 올림픽 주경기장', 69950);

-- ====================================================================
-- 2. 좌석(Seats) 데이터 추가
-- ====================================================================

-- == 1층 101구역 (10석) ==
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'A열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'A열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'A열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'A열', 4);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'A열', 5);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'B열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'B열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'B열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'B열', 4);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '1층 101구역', 'B열', 5);

-- == 2층 201구역 (10석) ==
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'A열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'A열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'A열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'A열', 4);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'A열', 5);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'B열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'B열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'B열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'B열', 4);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '2층 201구역', 'B열', 5);

-- == 3층 301구역 (10석) ==
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'A열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'A열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'A열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'A열', 4);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'A열', 5);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'B열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'B열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'B열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'B열', 4);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (1, '3층 301구역', 'B열', 5);

-- == 그라운드 G1구역 (10석) ==
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '1열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '1열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '1열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '2열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '2열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '2열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '3열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '3열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '3열', 3);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '그라운드 G1구역', '3열', 4);

-- == 1층 1-1구역 (10석) ==
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '1열', 11);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '1열', 12);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '1열', 13);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '1열', 14);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '2열', 11);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '2열', 12);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '2열', 13);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '2열', 14);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '2열', 15);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '1층 1-1구역', '2열', 16);

-- == 2층 2-1구역 (10석) ==
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '1열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '1열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '2열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '2열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '3열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '3열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '4열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '4열', 2);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '5열', 1);
INSERT INTO seats (venue_id, section, seat_row, seat_number) VALUES (2, '2층 2-1구역', '5열', 2);