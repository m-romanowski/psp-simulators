FROM gradle:9.6.1-jdk25 AS build
WORKDIR /workspace
ENV GRADLE_USER_HOME=/gradle-home
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN pinned="$(gradle --version | awk '/^Gradle /{print $2}')" \
    && grep -q "gradle-${pinned}-bin.zip" gradle/wrapper/gradle-wrapper.properties \
    && echo "gradle ${pinned} matches the wrapper"
COPY src/main src/main
RUN --mount=type=cache,id=demo-shop-gradle,target=/gradle-home \
    gradle bootJar --no-daemon --console=plain \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/demo-shop.jar \;

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --home-dir /app shop
WORKDIR /app
COPY --from=build /workspace/demo-shop.jar /app/demo-shop.jar
USER shop
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/demo-shop.jar"]
