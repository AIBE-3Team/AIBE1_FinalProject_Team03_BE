const fs = require('fs').promises;
const axios = require('axios');
const path = require('path');

// 설정
const USERS = Array.from({ length: 200 }, (_, i) => `K6TESTUSER${i + 1}`);
const PASSWORD = '1q2w3e4r!';
const API_BASE = 'http://localhost:8080/api';

// axios 인스턴스 생성
const api = axios.create({
    baseURL: API_BASE,
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    withCredentials: true,
});

// 쿠키 파싱 함수
function parseCookies(cookieHeaders) {
    const cookies = {};

    if (!cookieHeaders || !Array.isArray(cookieHeaders)) {
        console.warn('No cookie headers found');
        return cookies;
    }

    cookieHeaders.forEach(cookieHeader => {
        // 쿠키 헤더 예: "access=eyJhbGc...; Path=/; HttpOnly"
        const parts = cookieHeader.split(';');
        const [name, value] = parts[0].split('=');

        if (name && value) {
            cookies[name.trim()] = value.trim();
        }
    });

    return cookies;
}

// JWT 발급 함수 (개선된 버전)
async function getJwt(username) {
    try {
        const payload = `username=${encodeURIComponent(username)}&password=${encodeURIComponent(PASSWORD)}`;

        console.log(`로그인 시도: ${username}`);

        const res = await api.post('/auth/login', payload);

        // 응답 헤더 확인
        const setCookieHeaders = res.headers['set-cookie'];
        console.log(`${username} - Set-Cookie headers:`, setCookieHeaders);

        // 쿠키 파싱
        const cookies = parseCookies(setCookieHeaders);
        console.log(`${username} - Parsed cookies:`, cookies);

        // 토큰 추출
        const access = cookies.access;
        const refresh = cookies.refresh;

        if (!access || !refresh) {
            console.error(`${username} - 토큰 추출 실패:`, {
                access: access ? '있음' : '없음',
                refresh: refresh ? '있음' : '없음',
                cookies: cookies
            });
            return null;
        }

        // 토큰 유효성 간단히 확인 (JWT는 보통 'eyJ'로 시작)
        if (!access.startsWith('eyJ') || !refresh.startsWith('eyJ')) {
            console.warn(`${username} - 토큰 형식이 올바르지 않음`);
        }

        console.log(`${username} - 토큰 발급 성공`);

        return {
            username,
            access,
            refresh,
            // 디버깅용 추가 정보
            accessLength: access.length,
            refreshLength: refresh.length
        };

    } catch (err) {
        console.error(`JWT 발급 실패: ${username}`, {
            status: err.response?.status,
            statusText: err.response?.statusText,
            data: err.response?.data
        });
        return null;
    }
}

// 메인 실행 함수
(async () => {
    console.log('=== JWT 토큰 생성 시작 ===');
    console.log(`총 사용자 수: ${USERS.length}`);
    console.log(`API 엔드포인트: ${API_BASE}`);
    console.log('');

    // 병렬 실행을 위한 배치 처리 (10개씩)
    const batchSize = 10;
    const tokens = [];

    for (let i = 0; i < USERS.length; i += batchSize) {
        const batch = USERS.slice(i, i + batchSize);
        const promises = batch.map(user => getJwt(user));

        const results = await Promise.all(promises);
        const validTokens = results.filter(Boolean);
        tokens.push(...validTokens);

        console.log(`진행률: ${Math.min(i + batchSize, USERS.length)}/${USERS.length}`);

        // 서버 부하 방지를 위한 잠시 대기
        if (i + batchSize < USERS.length) {
            await new Promise(resolve => setTimeout(resolve, 100));
        }
    }

    console.log('');
    console.log(`=== 토큰 생성 완료 ===`);
    console.log(`성공: ${tokens.length}개`);
    console.log(`실패: ${USERS.length - tokens.length}개`);

    if (tokens.length === 0) {
        console.error('❌ 토큰이 하나도 생성되지 않았습니다!');
        process.exit(1);
    }

    // 토큰 샘플 확인
    if (tokens.length > 0) {
        console.log('\n첫 번째 토큰 샘플:');
        console.log(`- Username: ${tokens[0].username}`);
        console.log(`- Access Token 길이: ${tokens[0].accessLength}`);
        console.log(`- Refresh Token 길이: ${tokens[0].refreshLength}`);
        console.log(`- Access Token 시작: ${tokens[0].access.substring(0, 20)}...`);
    }

    // JSON 파일 저장
    const filePath = path.join(__dirname, 'k6-jwts.json');
    await fs.writeFile(filePath, JSON.stringify(tokens, null, 2));

    console.log(`\n✅ 파일 저장 완료: ${filePath}`);

    // k6 테스트를 위한 간단한 검증 스크립트 생성
    const verifyScript = `
// verify-jwt.js - JWT 토큰 검증 스크립트
const fs = require('fs');
const tokens = JSON.parse(fs.readFileSync('./k6-jwts.json', 'utf8'));

console.log('토큰 파일 검증:');
console.log('- 총 토큰 수:', tokens.length);
console.log('- 첫 번째 토큰:', {
    username: tokens[0].username,
    hasAccess: !!tokens[0].access,
    hasRefresh: !!tokens[0].refresh
});

// 모든 토큰이 필수 필드를 가지고 있는지 확인
const invalid = tokens.filter(t => !t.access || !t.refresh);
if (invalid.length > 0) {
    console.error('⚠️  무효한 토큰:', invalid.length);
} else {
    console.log('✅ 모든 토큰이 유효합니다');
}
`;

    await fs.writeFile(path.join(__dirname, 'verify-jwt.js'), verifyScript);
    console.log('✅ 검증 스크립트 생성: verify-jwt.js');
    console.log('   실행: node verify-jwt.js');
})();