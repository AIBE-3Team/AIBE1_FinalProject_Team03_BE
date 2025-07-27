import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

// 💡 아래 값을 조정하여 테스트 유저 수, 좌석 수를 지정할 수 있습니다.
const minSeat = 2000;
const maxSeat = 2059; // 60개 좌석 (2000-2059)
const TOTAL_USERS = parseInt(__ENV.TOTAL_USERS || '200');
const CONCURRENT_USERS = parseInt(__ENV.CONCURRENT_USERS || '100');
const RAMP_UP_DURATION = __ENV.RAMP_UP_DURATION || '60s';
const SCALE_UP_DURATION = __ENV.SCALE_UP_DURATION || '60s';
const TEST_DURATION = __ENV.TEST_DURATION || '300s';
const COOL_DOWN_DURATION = __ENV.COOL_DOWN_DURATION || '45s';

// 커스텀 메트릭 정의
// 대기열 관련
const queueEntrySuccess = new Counter('queue_entry_success');
const immediateEntryCount = new Counter('queue_immediate_entry');
const waitingEntryCount = new Counter('queue_waiting_entry');

// WebSocket 관련
const wsConnectionSuccess = new Counter('ws_connection_success');
const wsConnectionFailed = new Counter('ws_connection_failed');
const queueWaitTime = new Trend('queue_wait_time'); // 실제 대기 시간

// 좌석 예약 관련
const seatReservationSuccess = new Counter('seat_reservation_success');
const seatReservationFailed = new Counter('seat_reservation_failed');
const seatReservationRetries = new Trend('seat_retry_count');

const seatReservationTrend = new Trend('seat_reservation_duration');
const seatReleaseTrend = new Trend('seat_release_duration');

export const options = {
  scenarios: {
    load_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 30 },    // 2분에 걸쳐 30명까지 천천히 증가
        { duration: '2m', target: 30 },    // 30명 유지 (안정성 확인)
        { duration: '2m', target: 40 },    // 2분에 걸쳐 40명까지 증가
        { duration: '3m', target: 40 },    // 40명 유지
        { duration: '1m', target: 50 },    // 1분에 걸쳐 50명까지 증가
        { duration: '3m', target: 50 },    // 50명 유지 (절반 부하)
        { duration: '2m', target: 0 },     // 2분에 걸쳐 종료
      ],
      gracefulRampDown: '60s',
    }
  }
};

// 📌 기본 설정
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const API_BASE_URL = BASE_URL.endsWith('/api') ? BASE_URL : `${BASE_URL}/api`;
const WS_BASE_URL = __ENV.WS_BASE_URL || 'ws://localhost:8080';
const CONCERT_ID = __ENV.CONCERT_ID || '113';
const MIN_SEAT_ID = parseInt(__ENV.MIN_SEAT_ID || minSeat);
const MAX_SEAT_ID = parseInt(__ENV.MAX_SEAT_ID || maxSeat);
const MAX_RETRY_ATTEMPTS = 3;

// JWT 토큰 로드
const tokens = JSON.parse(open('./k6-jwts.json'));

// 세션 만료 시뮬레이션을 위한 랜덤 지연 함수
function getRandomSessionDuration() {
  // 30초~90초 사이의 랜덤한 세션 지속 시간
  return Math.random() * 60 + 30;
}

// 스마트 좌석 선택 함수
function selectOptimalSeat(attemptedSeats, userIndex) {
  const totalSeats = MAX_SEAT_ID - MIN_SEAT_ID + 1;

  // 사용자별 시작 위치 분산 (hot spot 방지)
  const userOffset = (userIndex * 7) % totalSeats; // 소수로 분산

  for (let i = 0; i < totalSeats; i++) {
    const seatOffset = (userOffset + i) % totalSeats;
    const targetSeatId = MIN_SEAT_ID + seatOffset;

    if (!attemptedSeats.has(targetSeatId)) {
      return targetSeatId;
    }
  }

  // 모든 좌석 시도했으면 랜덤
  return Math.floor(Math.random() * totalSeats) + MIN_SEAT_ID;
}

// WebSocket 대기 헬퍼 함수 (재연결 로직 포함)
function waitForQueueAdmission(wsUrl, authHeaders, username, maxWaitTime = 300000) {
  const waitStartTime = Date.now();
  let accessKey = null;
  const maxRetries = 3;
  let retryCount = 0;

  // 재연결 로직을 포함한 WebSocket 연결 함수
  function attemptConnection() {
    let connectionClosed = false;
    let wsSuccess = false;
    let shouldRetry = false;
    let wsConnected = false;

    console.log(`[${username}] WebSocket 연결 시도 ${retryCount + 1}/${maxRetries}`);
    console.log(`[${username}] WebSocket URL: ${wsUrl}`);

    const wsRes = ws.connect(wsUrl, authHeaders, function (socket) {
      wsConnected = true; // 연결 성공!
      wsConnectionSuccess.add(1);
      console.log(`[${username}] WebSocket 연결 성공! 세션 ID: ${socket.id}`);

      socket.on('open', () => {
        console.log(`[${username}] WebSocket open 이벤트 발생`);
      });

      socket.on('message', function (data) {
        try {
          const msg = JSON.parse(data);
          console.log(`[${username}] 메시지 수신:`, JSON.stringify(msg));

          if (msg.type === 'RANK_UPDATE') {
            console.log(`[${username}] 대기 순번: ${msg.rank}`);
          }

          if (msg.type === 'ADMIT' && msg.accessKey) {
            console.log(`[${username}] 입장 허가 받음! Access Key: ${msg.accessKey}`);
            const waitDuration = Date.now() - waitStartTime;
            queueWaitTime.add(waitDuration);
            accessKey = msg.accessKey;
            connectionClosed = true;
            socket.close();
          }
        } catch (e) {
          console.error(`[${username}] 메시지 파싱 오류:`, e);
        }
      });

      socket.on('error', function (e) {
        console.error(`[${username}] WebSocket 에러:`, e.error());
        shouldRetry = true;
        connectionClosed = true;
      });

      socket.on('close', () => {
        console.log(`[${username}] WebSocket 연결 종료`);
        connectionClosed = true;
      });

      // 타임아웃 설정
      socket.setTimeout(function () {
        console.log(`[${username}] WebSocket 타임아웃`);
        if (!accessKey) {
          shouldRetry = true;
        }
        connectionClosed = true;
        socket.close();
      }, maxWaitTime / (maxRetries - retryCount)); // 재시도할수록 타임아웃 줄임

      // 연결이 종료될 때까지 대기
      while (!connectionClosed) {
        sleep(0.5);
      }
    });

    // 연결 후 핸드셰이크 확인
    console.log(`[${username}] WebSocket 응답: ${wsRes.status}`);

    const handshakeSuccess = check(wsRes, {
      'WebSocket 핸드셰이크 성공': (r) => r && r.status === 101
    });

    if (!handshakeSuccess) {
      console.error(`[${username}] WebSocket 핸드셰이크 실패: ${wsRes ? wsRes.status : 'null'}`);
      shouldRetry = true;
    }

    return { success: wsSuccess && handshakeSuccess, shouldRetry: shouldRetry };
  }

  // 재연결 시도 루프
  while (retryCount < maxRetries && !accessKey) {
    const result = attemptConnection();

    if (accessKey) {
      // 성공적으로 access key를 받았음
      break;
    }

    if (result.shouldRetry && retryCount < maxRetries - 1) {
      retryCount++;
      const backoffTime = Math.min(1000 * Math.pow(2, retryCount), 5000); // 지수 백오프 (최대 5초)
      console.log(`[${username}] ${backoffTime/1000}초 후 WebSocket 재연결 시도...`);
      sleep(backoffTime / 1000);
    } else if (!result.shouldRetry) {
      // 재시도가 필요없는 상황 (예: 정상 종료)
      break;
    } else {
      // 최대 재시도 횟수 도달
      break;
    }
  }

  // 최종 실패 처리
  if (!accessKey) {
    console.error(`[${username}] WebSocket 연결 최종 실패 (시도: ${retryCount + 1}회)`);
    wsConnectionFailed.add(1);
  }

  return accessKey;
}

export default function () {
  // VU별로 다른 JWT 토큰 사용
  const userIndex = (__VU - 1) % tokens.length;
  const userToken = tokens[userIndex];

  console.log(`사용자 ${userToken.username} 테스트 시작 (VU: ${__VU})`);

  // JWT 토큰으로 쿠키 헤더 생성
  const cookieHeader = `access=${userToken.access}; refresh=${userToken.refresh}`;
  const authParams = {
    headers: {
      'Cookie': cookieHeader,
      'Content-Type': 'application/json'
    },
    timeout: '60s'  // 타임아웃 추가
  };

  // === 1단계: 대기열 진입 및 Access-key 획득 ===
  console.log(`[${userToken.username}] 대기열 진입 시도...`);
  const queueEnterRes = http.post(`${API_BASE_URL}/queue/enter?concertId=${CONCERT_ID}`, null, authParams);

  const queueEntryOk = check(queueEnterRes, {
    '대기열 진입 요청 성공': (r) => r.status === 200
  });

  if (!queueEntryOk) {
    console.error(`[${userToken.username}] 대기열 진입 실패: ${queueEnterRes.status} - ${queueEnterRes.body}`);
    return;
  }

  queueEntrySuccess.add(1);

  let queueData;
  try {
    const responseBody = JSON.parse(queueEnterRes.body);
    queueData = responseBody.data;
    console.log(`[${userToken.username}] 대기열 응답:`, JSON.stringify(queueData));
  } catch (e) {
    console.error(`[${userToken.username}] 응답 파싱 실패: ${queueEnterRes.body}`);
    return;
  }

  let accessKey = null;

  if (queueData.status === 'IMMEDIATE_ENTRY') {
    // 즉시 입장
    console.log(`[${userToken.username}] 즉시 입장 성공! Access Key: ${queueData.accessKey}`);
    immediateEntryCount.add(1);
    accessKey = queueData.accessKey;
  } else if (queueData.status === 'WAITING') {
    // 대기열 대기 (웹소켓 연결)
    console.log(`[${userToken.username}] 대기열 진입. 현재 순번: ${queueData.rank}`);
    waitingEntryCount.add(1);

    // WebSocket URL 구성
    const wsUrl = `${WS_BASE_URL}/ws/waitqueue?concertId=${CONCERT_ID}`;

    // WebSocket으로 대기 (동기적으로 처리)
    accessKey = waitForQueueAdmission(wsUrl, authParams, userToken.username);
  }

  // Access-key 획득 실패 시 테스트 중단
  if (!accessKey) {
    console.error(`[${userToken.username}] Access-key 획득 실패. 테스트를 종료합니다.`);
    return;
  }

  // 인증 헤더 업데이트 (JWT + Access-key)
  const finalAuthParams = {
    headers: {
      'Cookie': cookieHeader,
      'Content-Type': 'application/json',
      'X-Access-Key': accessKey,
    },
    timeout: '30s'
  };

  // 서버 처리를 위한 짧은 대기
  sleep(1);

  // === 2단계: 좌석 예약 시도 (최대 3번 재시도) ===
  let reservationSuccess = false;
  let reservedSeatId = null;
  let attemptCount = 0;
  const attemptedSeats = new Set();

  while (!reservationSuccess && attemptCount < MAX_RETRY_ATTEMPTS) {
    attemptCount++;

    // 스마트 좌석 선택
    const targetSeatId = selectOptimalSeat(attemptedSeats, userIndex);
    attemptedSeats.add(targetSeatId);

    console.log(`[${userToken.username}] 좌석 예약 시도 ${attemptCount}/${MAX_RETRY_ATTEMPTS}: 좌석 ID ${targetSeatId}`);

    // 좌석 예약 요청
    const reservationStartTime = Date.now();
    const reservationResponse = http.post(
      `${API_BASE_URL}/seats/concerts/${CONCERT_ID}/seats/${targetSeatId}/reserve`,
      null,
      {
        ...finalAuthParams,
        tags: { name: 'seat_reservation' },
      }
    );
    const reservationDuration = Date.now() - reservationStartTime;
    seatReservationTrend.add(reservationDuration);

    // 상태 코드별 처리
    if (reservationResponse.status === 200) {
      reservationSuccess = true;
      seatReservationSuccess.add(1);
      reservedSeatId = targetSeatId;
      console.log(`[${userToken.username}] 좌석 ${targetSeatId} 예약 성공! ✅`);
    } else if (reservationResponse.status === 400) {
      console.log(`[${userToken.username}] 좌석 ${targetSeatId} 이미 예약됨`);
    } else if (reservationResponse.status === 401) {
      console.error(`[${userToken.username}] 인증 실패: ${reservationResponse.body}`);
      break; // 인증 실패시 재시도 무의미
    } else {
      console.error(`[${userToken.username}] 예약 실패: ${reservationResponse.status} - ${reservationResponse.body}`);
    }

    // 재시도 전 대기
    if (!reservationSuccess && attemptCount < MAX_RETRY_ATTEMPTS) {
      sleep(Math.random() * 2 + 1); // 1-3초 랜덤 대기
    }
  }

  seatReservationRetries.add(attemptCount);

  if (!reservationSuccess) {
    seatReservationFailed.add(1);
  }

  // === 3단계: 예약 성공한 경우 테스트 유지 ===
  if (reservationSuccess && reservedSeatId) {
    console.log(`[${userToken.username}] 좌석 ${reservedSeatId} 예약 유지 중...`);

    // 일정 시간 예약 유지 (실제 사용 패턴 시뮬레이션)
    sleep(Math.random() * 10 + 5); // 5-15초 유지

    // 좌석 해제 (선택적)
    const releaseStartTime = Date.now();
    const releaseResponse = http.del(
      `${API_BASE_URL}/seats/concerts/${CONCERT_ID}/seats/${reservedSeatId}/release`,
      null,
      finalAuthParams
    );
    const releaseDuration = Date.now() - releaseStartTime;
    seatReleaseTrend.add(releaseDuration);

    if (releaseResponse.status === 200) {
      console.log(`[${userToken.username}] 좌석 ${reservedSeatId} 해제 성공`);
    } else {
      console.error(`[${userToken.username}] 좌석 해제 실패: ${releaseResponse.status}`);
    }
  }

  // 최종 결과 로깅
  console.log(`[${userToken.username}] 테스트 완료: 예약시도 ${attemptCount}회, 예약성공 ${reservationSuccess}`);

  // 사용자별 대기 시간
  sleep(Math.random() * 2 + 1);
}

export function teardown(data) {
  console.log('\n=== 좌석 예약 부하 테스트 완료 ===');
  console.log(`총 사용자 수: ${tokens.length}명`);
  console.log(`콘서트 ID: ${CONCERT_ID}`);
  console.log(`좌석 범위: ${MIN_SEAT_ID}-${MAX_SEAT_ID} (${MAX_SEAT_ID - MIN_SEAT_ID + 1}개)`);

  console.log('\n=== 주요 메트릭 ===');
  console.log(`대기열 진입 성공: ${queueEntrySuccess.value || 0}`);
  console.log(`즉시 입장: ${immediateEntryCount.value || 0}`);
  console.log(`대기열 대기: ${waitingEntryCount.value || 0}`);
  console.log(`WebSocket 연결 성공: ${wsConnectionSuccess.value || 0}`);
  console.log(`WebSocket 연결 실패: ${wsConnectionFailed.value || 0}`);
  console.log(`좌석 예약 성공: ${seatReservationSuccess.value || 0}`);
  console.log(`좌석 예약 실패: ${seatReservationFailed.value || 0}`);

  console.log('\n=== 응답 시간 통계 ===');
  if (seatReservationTrend.avg) {
    console.log(`좌석 예약 평균 응답 시간: ${seatReservationTrend.avg.toFixed(2)}ms`);
  }
  if (seatReleaseTrend.avg) {
    console.log(`좌석 해제 평균 응답 시간: ${seatReleaseTrend.avg.toFixed(2)}ms`);
  }
}

export function setup() {
  console.log('=== 좌석 예약 부하 테스트 시작 ===');
  console.log(`API URL: ${API_BASE_URL}`);
  console.log(`WebSocket URL: ${WS_BASE_URL}`);
  console.log(`Concert ID: ${CONCERT_ID}`);
  console.log(`좌석 ID 범위: ${MIN_SEAT_ID}~${MAX_SEAT_ID}`);
  console.log(`JWT 토큰 수: ${tokens.length}개`);
  console.log(`Max Active Users (서버 설정): 100명`);

  return {
    startTime: new Date(),
  };
}