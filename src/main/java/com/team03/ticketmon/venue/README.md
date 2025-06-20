### **`venue` 패키지: '장소' 도메인 신설**

- 물리적인 장소 및 좌석 정보 관리
* **책임**: 
  * 공연장, 물리적인 좌석 배치 등 재사용 가능한 '장소' 정보를 관리
  * 나중에 뮤지컬, 팬미팅 등 다른 종류의 이벤트를 추가하더라도 이 도메인을 그대로 활용 가능
* **주요 구성 요소**:
  * **Entity**: `Venue`, `Seat` 
  * **Service/Repository**: `VenueService`, `VenueRepository` 등
