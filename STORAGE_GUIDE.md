## 📦 파일 업로드 시스템 구조
> 📄 이 문서는 Supabase → S3 마이그레이션을 고려한 **공통 파일 업로드 시스템** 구조를 설명합니다.  
> 루트 README에서 오신 경우: [← 메인으로 돌아가기](./README.md)


---
### ✅ 공통 업로더 인터페이스
```java
public interface StorageUploader {
String uploadFile(MultipartFile file, String bucket, String path);
}
```
- Supabase / S3 공통 업로드 방식 추상화
- 추후 S3 마이그레이션 시 `StorageUploader`만 교체하면 기존 서비스 로직 코드 그대로 유지 가능


<br>

### 🧱 현재 구현체
|구현체|	설명| 	비고                                     |
|-|-|-----------------------------------------|
|`SupabaseUploader`|	Supabase Storage SDK 기반 업로드| 	("supabase") 적용 (현재 사용 중)              |
|`S3Uploader`|	S3 클라이언트 기반 업로드 구현체| 	🔒@Profile("s3") 적용 (마이그레이션 대비 구현 완료, 현재 비활성 상태) |


<br>

### 🛠️ 의존성
```groovy
// Supabase Storage 연동용 SDK
implementation 'io.supabase:storage-java:1.1.0'

// AWS Spring Cloud S3 연동 (Spring Boot 3.x 이상)
implementation 'io.awspring.cloud:spring-cloud-aws-starter-s3:3.4.0'
```
- io.awspring.cloud:spring-cloud-aws-starter-s3는 내부적으로 AWS SDK for S3 (software.amazon.awssdk:s3)를 포함합니다.

<br>

### ⚙️ 설정 파일 구조
**🔐 (.env.example)[.env.example]**

**🔧 application-dev.yml ((application-prod)[src/main/resources/application-prod.yml] 참고)**

<br>

### 🚀 S3 마이그레이션 전략
|항목|	Supabase 구조|	S3 구조 (계획)|
|-|-|-|
|버킷|	기능별 분리 (3개)|	하나의 버킷 사용|
|구분|	버킷 기준|	prefix(폴더) 기준|
|예시|	ticketmon-dev-profile-imgs|	ticketmon-prod-assets/profile-imgs/{userId}.jpg|

<br>

### 🔄 마이그레이션 시 할 일
1. .env에서 AWS S3 환경변수 활성화 및 실제 값 설정
2. application.yml의 spring.profiles.include를 s3로 변경 (또는 배포 시 SPRING_PROFILES_ACTIVE=prod로 설정하여 application-prod.yml의 include: s3가 적용되도록)
3. application-prod.yml에서 Supabase 설정 블록 주석 처리 (또는 제거), S3 설정 블록 주석 해제 (또는 활성화)
4. 필요 시 업로드 경로 변경 (prefix 구조 적용)

<br>
<br>
<br>

---
## ✅ 추가 참고 항목
### 1. ✅ 업로더 사용 예시 (서비스에서 어떻게 사용하는지)
```java
@Autowired
private StorageUploader storageUploader;

String imageUrl = storageUploader.uploadFile(file, bucketName, path);
```
> 📌 Supabase/S3 상관없이 이 방식으로 동일하게 사용 가능

<br>

### 2. 🔀 Profile 설정 흐름 요약
```yaml
# application.yml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev} # 로컬 개발 기본값 'dev'
    include: supabase # 어떤 active 프로필이든 supabase 관련 빈 포함

# application-prod.yml
spring:
  profiles:
    # prod 프로필이 활성화되면 's3' 프로필을 포함하여 S3 관련 빈 로드
    include: s3

# application-dev.yml (참고용 - 실제 파일에는 include 없음)
# spring:
#   profiles:
#     # application.yml의 'include: supabase'에 의해 자동으로 포함됨
#     # 여기에 include를 명시하면 InvalidConfigDataPropertyException이 발생할 수 있음
#     # include: supabase
```
> 💡 최종 활성 프로필에 따라 Supabase 또는 S3 관련 빈이 조건부로 로드됩니다.

<br>

### 3. 📍 파일 경로(path) 설계 규칙 예시
|구분|	설명| 	예시                          |
|-|-|------------------------------|
|프로필 이미지|	유저 ID 기반 파일명| 	profile-imgs/{userId}.jpg   |
|포스터 이미지|	콘서트 ID 기반| 	poster-imgs/{concertId}.png |
|판매자 문서|	UUID 기반 문서| 	seller-docs/{docId}.pdf     |
> ✏️ path는 서비스 로직에서 결정해서 uploadFile() 호출 시 전달해야 함

<br>

### 4. 📁 파일 크기 / 확장자 제한 (현재 적용 중이라면)
```java
// 예시
if (file.getSize() > MAX_SIZE) {
throw new IllegalArgumentException("파일 크기 제한 초과");
}
```
- ✅ 권장: 2MB 이하
- ✅ 허용 확장자: jpg, png, webp, pdf (필요에 따라)

<br>

### 5. 🧪 로컬 테스트 가이드 (Swagger, Postman 등)
```http
PATCH /api/v1/users/me/profile/image
Content-Type: multipart/form-data

[file] = test.jpg
```
> ⚠️ Swagger에서 multipart 업로드 시 버그 있을 수 있으므로 Postman 추천
