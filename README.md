# AIBE1-FinalProject-Team03

---


## (2025.06.15) 초기 환경 세팅 관련 공지

---
## 1. 초기 환경 설정 요약 (Security + Swagger)

**✅ 적용 대상**

- `SecurityConfig.java`
- `SwaggerConfig.java`

---

### 🔐 Spring Security 설정 요약

| 구분 | 설명                                                                    |
| --- |-----------------------------------------------------------------------|
| **현재 인증 상태** | **전체 인증 없이 테스트 가능** 상태입니다.→ `anyRequest().permitAll()`                |
| **JWT 필터** | 자리만 확보되어 있으며, **실제 필터는 구현되지 않았습니다.**<br>→ 로그인/회원가입/JWT 담당자가 추후 구현 예정  |
| **권한 설정** | `/admin/**` 등 권한별 URL은 **아직 작동하지 않으며**, 주석으로 표시되어 있습니다.               |
| **인증 예외 응답 처리** | 인증 실패(401), 권한 없음(403)에 대해 커스텀 에러 메시지를 반환하도록 설정됨.                     |
| **로그아웃 설정** | `/logout` 호출 시 세션 무효화 + JWT 쿠키 삭제 설정만 되어있습니다.<br> → JWT 무효화는 실제 구현 필요 |
| **CORS 설정** | 프론트 요청 허용(`localhost:3000`, `localhost:8080`) 및 쿠키/헤더 허용 구성 완료        |

🔧 *📌 현재 인증 설정은 모두 테스트 편의를 위한 임시 상태입니다. 실제 로그인 기능 구현 시 반드시 수정이 필요합니다.*

<br>

---

### 📘 Swagger 설정 요약

| 구분 | 설명 |
| --- | --- |
| **접근 경로** | `/swagger-ui/index.html` 에서 UI 확인 가능 |
| **보안 설정** | JWT 사용을 전제로 `Authorize` 버튼이 설정되어 있음 (`bearer` 방식) |
| **실제 인증 작동 여부** | ❌ 현재는 JWT 미연동 상태이므로 `Authorize` 버튼은 **기능하지 않습니다.** |
| **구성 목적** | 이후 JWT 기능 구현 시 Swagger에서도 로그인 후 API 테스트 가능하게 하기 위함입니다. |

<br>

---

### 📌 팀원별 작업 참고 사항

| 역할 | 확인해야 할 부분 |
| --- | --- |
| ✅ **로그인/회원가입/JWT 담당자** | - `JwtAuthenticationFilter` 구현 후 SecurityConfig의 주석 해제 <br>- Swagger와 JWT 연동 적용 필요 |
| ✅ **API 개발 담당자** | - 현재는 인증 없이 API 호출 가능하지만, **향후 인증 필요**를 고려한 설계 권장 |
| ✅ **인프라 담당자** | - SecurityConfig, SwaggerConfig 위치와 현재 상태 숙지 <br>- 추후 인증 정책 변경 시 전체 테스트 환경 영향 고려 |
| 🧪 **테스트 시 참고** | - 현재 Swagger에서는 인증 없이 모든 API 호출 가능 <br>- 로그인 후 토큰이 생기면, Swagger Authorize 버튼을 통해 인증 테스트 가능 예정 |

<br>

---

### ✍️ 현재 SecurityConfig 주요 코드 상태 (요약)

```java
// 현재 모든 API 인증 없이 허용
.anyRequest().permitAll()

// 향후 인증 설정 전환용 주석
// .anyRequest().authenticated()

// JWT 필터 자리만 확보 (구현 전)
// http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

<br>
<br>
<br>

---
## 2. 📛 공통 예외 처리 정책 (Global Exception Handling)
### ✅ 목적
- 예외 응답을 일관된 형식으로 내려주기 위해 설정합니다.
- 에러 코드와 메시지를 한 곳에서 통합 관리함으로써 유지보수를 쉽게 합니다.
- 개발자/사용자 모두에게 명확한 에러 정보를 제공합니다.

---
### 🧩 기본 응답 형식
**❗ 실패(Error) 응답 (`ErrorResponse`)**
```json
{
  "success": false,
  "status": 400,
  "code": "C001",
  "message": "유효하지 않은 입력값입니다."
}
```

**✅ 성공(Success) 응답 (`SuccessResponse`)**
```json
{
  "success": true,
  "message": "조회 성공",
  "data": {
    "title": "콘서트 A",
    "date": "2025-12-01"
  }
}
```

<br>

---
### ⚙️ 전체 구성도
```text
[Controller] → 예외 발생
↓
[BusinessException] 또는 기타 Exception
↓
[GlobalExceptionHandler]에서 처리
↓
ErrorCode에 따라 ErrorResponse 생성
↓
클라이언트에 통일된 에러 응답 반환
```

<br>

---

### 🧱 주요 구성 요소 설명
|파일명|	역할|
|-|-|
|`ErrorCode.java`|	❗ 전체 에러 코드/메시지 관리 (enum으로 구분)|
|`BusinessException.java`|	직접 정의한 예외(비즈니스 로직 위반)를 처리하는 커스텀 예외 클래스|
|`ErrorResponse.java`|	실패 응답 포맷 클래스 (`success: false`)|
|`SuccessResponse.java`|	성공 응답 포맷 클래스 (`success: true`)|
|`GlobalExceptionHandler.java`|	전역 예외 핸들러 (`@RestControllerAdvice`)|

<br>

---

### ✏️ 사용 예시
**1. ❗ 비즈니스 예외 발생**
```java
   if (seat.isAlreadyBooked()) {
        throw new BusinessException(ErrorCode.SEAT_ALREADY_TAKEN);
   }
```
→ 클라이언트에 아래처럼 반환됨:

```json
{
  "success": false,
  "status": 409,
  "code": "B003",
  "message": "이미 선택된 좌석입니다."
}
```
<br>

**2. ✅ 성공 응답 반환**
```java
   return ResponseEntity.ok(SuccessResponse.of("등록 성공", concertDto));
```
→ 클라이언트에 아래처럼 반환됨:

```json
{
  "success": true,
  "message": "등록 성공",
  "data": {
    "concertId": 12,
    "title": "Waiting Cha Live"
  }
}
```

<br>

---

### 💡 팀원 참고사항
| 항목                                                    |	설명|
|-------------------------------------------------------|-|
| **에러 코드는 어디에 정의하나요?**                                 |	`ErrorCode.java` enum에 추가하세요|
| **새로운 예외 만들려면?**                                      |	`throw new BusinessException(ErrorCode.원하는_코드);`|
| **공통 에러 메시지는 어디서 바꾸나요?**                              |	`ErrorCode.java`의 `message` 필드에서 수정|
| **예외 직접 잡고 싶을 땐?**                                    |	`@ExceptionHandler(YourException.class)` 추가 가능|
| **응답에 `success`, `message`, `data` 일관되게 나오게 하고 싶다면?** |	반드시 `SuccessResponse`와 `ErrorResponse`를 사용하세요|


<br>
<br>

---

---

## 🐳 Docker 개발 환경 구성 가이드

> 로컬 개발 환경을 통일하기 위해 Docker를 활용한 구성 가이드를 제공합니다.


<br>

---
### ✅ 구성 요소

| 서비스           | 설명 |
|---------------|------|
| `spring-app`  | 백엔드 애플리케이션 (Dockerfile 기반) |
| `redis-cache` | Redis (좌석 캐싱 및 분산락 처리용) |
|`localstack`|LocalStack (AWS 서비스 모킹: SQS, S3 등 개발용)|
| `(MySQL)`     | DB는 개발 초기에는 Aiven 사용, 추후 AWS Aurora로 마이그레이션 예정 |

<br>

---

### 📁 전제 조건

1. Docker Desktop 설치 (https://www.docker.com/products/docker-desktop)
2. **`.env`, `application-dev.yml` 파일 수령 및 배치**
- `application-dev.yml`, `.env`은 직접 전달 예정
- `application-dev.yml`는 `src/main/resources/`에 `application-dev.yml`로 위치
- `.env`는 팀 디스코드 또는 팀원 공유 경로에서 .env 파일을 받아, 프로젝트 루트 디렉토리에 위치
3. `.env.example`은 참고용으로 제공됩니다.

<br>

---

### 📦 실행 방법

1. `.env` 파일 수령 및 배치

> 팀 디스코드 또는 팀원 공유 경로에서 `.env` 파일을 받아, 루트 디렉토리에 위치시킵니다.

2. Docker 이미지 빌드 및 컨테이너 실행

  ```bash
  docker-compose up --build
  ```

3. 컨테이너 상태 확인
  ```bash
  docker ps
  ```

4. 컨테이너 중지
  ```bash
  docker-compose down
  ```

<br>

---

### 📂 주요 파일 설명
|파일명|	설명|
|-|-|
|Dockerfile|	Spring Boot 애플리케이션 빌드 및 실행 설정|
|docker-compose.yml|	전체 서비스(앱 + Redis + LocalStack) 컨테이너 정의|
|.dockerignore|	빌드 시 Docker에 포함하지 않을 파일 목록|
|.env.example|	환경변수 예시 파일 (민감 정보는 포함되지 않음)|
|.env|로컬 개발 환경 변수 (실제 민감 정보 포함, Git 추적 제외)|
|application.yml|Spring 공통 설정 및 프로필 활성화 기준 정의|
|application-dev.yml|개발 환경 (dev 프로필) 전용 설정|
|application-prod.yml|운영 환경 (prod 프로필) 전용 설정|

<br>

---

### 🧪 테스트 포인트
- localhost:8080에서 백엔드 서비스 확인
- localhost:6379 Redis 정상 기동 여부 확인
- LocalStack SQS: docker exec -it localstack awslocal sqs list-queues 명령으로 SQS 서비스 확인
- Swagger: http://localhost:8080/swagger-ui/index.html

<br>

---

### ⚠️ 보안 및 주의사항
- .env 및 application-dev.yml은 절대 Git에 업로드하지 마세요!
  - .gitignore에 이미 포함되어 있음
  - 팀원 간 수동 전달 방식 유지
- 환경 변수 누락 시 실행 에러가 발생할 수 있습니다.

<br>

---
## ✅ 초기 세팅 가이드 (infra 담당자용)
> 티켓팅 프로젝트의 로컬 개발 및 Docker 실행을 위한 환경 설정 가이드입니다. <br> Aiven MySQL, Redis, Supabase, LocalStack, Swagger 연동까지 한 번에 확인 가능하도록 구성했습니다.

---

### 📁 폴더 및 파일 구성 요약
|파일명	| 설명                                                             |
|-|----------------------------------------------------------------|
|`.env`| 	실제 민감 설정 키 (Aiven, Supabase, JWT 등) – ❌ Git 제외 / ✅ Discord 공유 |
|`.env.example`| 	키 없이 형식만 제공되는 예시 파일 – ✅ Git 포함                                |
|`application.yml`| 	Spring 공통 설정 (프로필 활성화 및 기본 include 포함)                                                 |
|`application-dev.yml`| 	개발 환경 (dev 프로필) 전용 설정                              |
|`application-prod.yml`|운영 환경 (prod 프로필) 전용 설정|
|`docker-compose.yml`| 	Redis, LocalStack, Spring Boot 실행용 컨테이너 구성                    |
|`Dockerfile`| 	Spring Boot 앱의 도커 빌드 설정                                       |

<br>

### ✅ 설정 방식 요약
|항목|	방식|
|-|-|
|프로필 적용|	application.yml의 ${SPRING_PROFILES_ACTIVE:dev} 변수 참조 → application-dev.yml 또는 application-prod.yml 로드. (application.yml의 include: supabase 설정도 함께 적용)|
|.env 연동 방식|	application-dev.yml에서 spring.config.import: optional:file:.env[.properties] 설정 사용|
|민감 키 관리|	.env에만 저장 / application-dev.yml 및 application-prod.yml에는 ${VAR_NAME} 변수명만 작성|
|Docker 실행|	docker-compose up 한 번으로 Redis, LocalStack, Spring Boot 앱 실행 가능|

<br>

### ✅ 체크리스트
|항목| 	설명                                                                                    | 	상태                                            |
|-|----------------------------------------------------------------------------------------|------------------------------------------------|
|🔄 docker-compose up 후 Redis/LocalStack 기동 확인| 	Redis: redis-cli ping → PONG 반환, LocalStack: awslocal sqs list-queues 오류 없이 반환        | 	[x]                                           |
|📄 .env에 실제 키값 반영| 	Discord 등 별도 채널로 공유 (실제 값으로 대체 필요)                                                    | 	[] <br> (작성 완료)                               |
|🔗 .env → application-dev.yml 연동 확인| 	Spring 로그에 Using config data from 'file:.env[.properties]' 확인                         | 	☐                                             |
|🌐 Swagger 접속 확인| 	http://localhost:8080/swagger-ui/index.html	                                          | [x]                                            |
|🪣 Supabase 버킷 생성 + 정책 설정| 	ticketmon-dev-profile-imgs, ticketmon-dev-poster-imgs, ticketmon-dev-seller-docs 등 생성 | 	[x] (설정 완료 / 2MB 제한 + MIME 필터 설정은 구현 단계에서 설정) |
|🐳 Docker로 Spring Boot 실행 후 .env 반영 확인| 	Swagger 정상 접속 + DB 연결 여부 (로그 확인) | 	[x]|

<!--
## ✅ 초기 세팅 체크리스트 (2025-06 기준)

| 항목 | 설명 | 상태 |
|------|------|------|
| 🔄 `docker-compose up` 후 Redis/LocalStack 기동 확인 | Redis `PONG` 응답 확인 / LocalStack SQS 명령어 정상 작동 | ✅ 완료 |
| 📄 `.env`에 실제 키값 반영 | `.env` 파일에는 키가 반영되어 있지만, 현재 application-dev.yml 직접 사용 중 | 🔄 작성 완료 (연동 미완료) |
| 🔗 `.env → application-dev.yml` 연동 테스트 | `spring.config.import=optional:env[.env]` 방식은 아직 미적용 | ⛔ 미완료 |
| 🌐 Swagger 접속 확인 | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) 정상 접속 확인 | ✅ 완료 |
| 🪣 Supabase 버킷 및 정책 설정 | `profile-imgs`, `poster-imgs` 등 버킷 미생성 상태 | ⛔ 미완료 |
| 🐳 Docker로 실행 시 `.env` 적용 여부 | `.env` → SpringBoot 설정이 미적용 상태라 확인 불가 | ⛔ 미완료 |
-->

<br>

### 🛠 확인 명령어 정리
**Redis 상태 확인**
```bash
docker exec -it redis-cache redis-cli ping
# → PONG 반환되면 정상 작동
```
**LocalStack SQS 상태 확인**
```bash
docker exec -it localstack awslocal sqs list-queues
# → 큐 없어도 200 OK 반환되면 정상
```

<br>

### 💬 참고 사항
- .env는 Git에 포함되지 않으며, 형식 제공용 .env.example 파일로 대체됩니다
- Supabase와 LocalStack을 개발 초기 환경으로 사용하며, S3/SQS는 추후 마이그레이션 예정입니다.
- 현재의 docker-compose.yml은 개발용 설정이며, 운영 환경에서는 별도 설정 예정입니다.

<br>
<br>

---

---

### + [추가 문서]
- 📎 [TimeUtil 설명 보기](src/main/java/com/team03/ticketmon/_global/util/TIME_UTIL_GUIDE.md)
- 📦 [파일 업로드 시스템 / 파일 업로드 구조 보기](./STORAGE_GUIDE.md)