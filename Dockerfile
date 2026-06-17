# syntax=docker/dockerfile:1
# amd64 배포용 멀티스테이지 빌드. (Mac arm64에서 빌드해도 linux/amd64 산출물 보장)

### 1) 빌드 스테이지 — JDK 26로 bootJar 생성
FROM --platform=linux/amd64 eclipse-temurin:26-jdk AS build
WORKDIR /workspace

# 래퍼/빌드 스크립트 먼저 복사해 의존성 레이어 캐시
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 실행 가능 jar 빌드 (테스트 제외)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v plain | head -n1)" /workspace/app.jar

### 2) 런타임 스테이지 — JRE만, 비루트 실행
FROM --platform=linux/amd64 eclipse-temurin:26-jre AS runtime
WORKDIR /app

# actuator 헬스체크용 curl
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app

COPY --from=build /workspace/app.jar app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080

# RAM 최소화: 컨테이너 메모리 비율 기반 힙 + SerialGC(저메모리 친화)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
