# syntax=docker/dockerfile:1

# 1) bootJar 빌드
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar -x test \
	&& find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# 2) 실행: 공유 링크 수집용 Chromium이 든 Playwright 이미지 + Java 21 JRE
#    Playwright 버전은 build.gradle의 com.microsoft.playwright 버전과 반드시 같아야 한다.
FROM mcr.microsoft.com/playwright/java:v1.63.0-noble
COPY --from=eclipse-temurin:21-jre /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
	PATH=/opt/java/openjdk/bin:$PATH \
	PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
RUN mkdir -p data && chown -R pwuser:pwuser /app
USER pwuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
