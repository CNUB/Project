# FinanceScope - 금융 뉴스 분석 및 관리 플랫폼

## 프로젝트 소개

**FinanceScope**는 금융 뉴스 데이터를 수집하고 분석하여 사용자에게 제공하는 백엔드 서비스입니다. 이 프로젝트는 최신 금융 동향을 파악하고, 뉴스에 담긴 시장의 감성을 분석하며, 관련 경제 지표를 시각화하는 것을 목표로 합니다.

Spring Boot를 기반으로 구축되었으며, 안정적인 백엔드 시스템을 위한 다양한 기술과 아키텍처 패턴을 적용했습니다.


[![시연 영상 보기](https://img.youtube.com/vi/TUpKqTXrDwk/0.jpg)](https://www.youtube.com/watch?v=TUpKqTXrDwk)

클릭 시 데모 영상으로 이동

## 주요 기능

- **사용자 인증**: JWT(JSON Web Token)를 이용한 안전한 회원가입 및 로그인 기능을 제공합니다.
- **뉴스 크롤링**: 지정된 금융 뉴스 사이트에서 기사를 수집합니다. (*현재는 실제 크롤링 대신 더미 데이터를 사용하고 있습니다.*)
- **뉴스 요약**: 긴 분량의 뉴스 기사를 핵심만 파악할 수 있도록 AI 모델을 통해 요약합니다.
- **감성 분석**: 뉴스의 긍정/부정/중립 톤을 분석하여 시장의 감성 지수를 측정합니다.
- **경제 지표 분석**: 수집된 뉴스 데이터와 경제 지표를 연관지어 분석 리포트를 제공합니다.
- **뉴스 클러스터링**: 유사한 주제의 뉴스 기사를 그룹화하여 보여줍니다.
- **API 문서화**: Swagger UI를 통해 API 명세를 실시간으로 확인하고 테스트할 수 있습니다.

## 기술 스택

- **Backend**: Java 21, Spring Boot 3, Spring Security
- **Database**: MySQL
- **Data Access**: Spring Data JPA (Hibernate)
- **Authentication**: JSON Web Tokens (JWT)
- **API**: RESTful API, Spring WebFlux (`WebClient`)
- **Web Scraping**: Jsoup
- **Build Tool**: Gradle
- **API Documentation**: SpringDoc (Swagger UI)

## 시작하기

### 1. 실행 환경

- Java 21
- Gradle 8.x
- MySQL 8.0

### 2. 프로젝트 설정

1.  **저장소 복제**
    ```bash
    git clone [https://github.com/your-username/financescope-portfolio.git](https://github.com/your-username/financescope-portfolio.git)
    cd financescope-portfolio
    ```

2.  **로컬 설정 파일 생성**
    `src/main/resources/` 경로에 [application-local.properties](cci:7://file:///c:/Users/vjwmf/OneDrive/%EB%B0%94%ED%83%95%20%ED%99%94%EB%A9%B4/Back/financescope/src/main/resources/application-local.properties:0:0-0:0) 파일을 새로 만듭니다. 이 파일은 Git에 의해 추적되지 않으며, 민감한 로컬 환경 정보를 담는 데 사용됩니다.

3.  **설정 정보 입력**
    아래 내용을 [application-local.properties](cci:7://file:///c:/Users/vjwmf/OneDrive/%EB%B0%94%ED%83%95%20%ED%99%94%EB%A9%B4/Back/financescope/src/main/resources/application-local.properties:0:0-0:0) 파일에 복사한 뒤, 자신의 로컬 환경에 맞게 값을 수정해주세요.

    ```properties
    # Local Development Properties

    # MySQL 연결 설정
    spring.datasource.url=jdbc:mysql://localhost:3306/financescope?useSSL=false&serverTimezone=UTC
    spring.datasource.username=[YOUR_DB_USERNAME]
    spring.datasource.password=[YOUR_DB_PASSWORD]
    spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

    # JWT 시크릿 키 (임의의 긴 문자열로 설정)
    jwt.secret=your-very-long-and-secret-jwt-key-that-no-one-can-guess

    # 이메일 발송 관련 (Gmail 기준)
    spring.mail.username=[YOUR_GMAIL_ADDRESS]
    spring.mail.password=[YOUR_GMAIL_APP_PASSWORD]
    ```
    > **참고**: Gmail을 사용하려면, 구글 계정에서 '앱 비밀번호'를 생성하여 사용해야 합니다.

4.  **애플리케이션 실행**
    프로젝트를 빌드하고 실행합니다.
    ```bash
    ./gradlew build
    java -jar build/libs/financescope-0.0.1-SNAPSHOT.jar
    ```

### 3. API 문서 확인

애플리케이션 실행 후, 아래 주소로 접속하여 모든 API 엔드포인트를 확인하고 테스트할 수 있습니다.
- **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## 프로젝트 현황 및 개선점

- **크롤링 기능**: 현재 [CrawlService](cci:2://file:///c:/Users/vjwmf/OneDrive/%EB%B0%94%ED%83%95%20%ED%99%94%EB%A9%B4/Back/financescope/src/main/java/com/financescope/financescope/service/CrawlService.java:16:0-118:1)는 실제 웹 크롤링을 수행하는 대신, 시뮬레이션을 위한 더미 데이터를 반환하고 있습니다. 향후 실제 크롤링 로직을 구현하여 다양한 뉴스 소스에서 데이터를 수집하는 기능이 필요합니다.
- **외부 API 연동**: 뉴스 요약 및 감성 분석 기능은 외부 AI 서비스를 호출하는 구조로 설계되었습니다. 실제 서비스 연동을 통해 기능을 완성할 수 있습니다.
- **성능 최적화**: 대용량 데이터 처리를 위해 캐싱 전략(예: Redis)을 고도화하고, 데이터베이스 쿼리를 최적화할 수 있습니다.
