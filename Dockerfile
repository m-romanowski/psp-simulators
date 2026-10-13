# Builds the simulator's jar inside this image, so the machine running
# `docker compose up` needs no JDK and no Gradle of its own.
#
# The build stage ships Gradle rather than letting ./gradlew fetch it: that
# download redirects to GitHub releases, which answered 503 often enough to
# fail the one command a newcomer runs. The image tag and the wrapper must
# therefore agree on the version, and the check below fails the build if they
# ever drift apart.
FROM gradle:9.6.1-jdk25 AS build
WORKDIR /workspace
ENV GRADLE_USER_HOME=/gradle-home
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN pinned="$(gradle --version | awk '/^Gradle /{print $2}')" \
    && grep -q "gradle-${pinned}-bin.zip" gradle/wrapper/gradle-wrapper.properties \
    && echo "gradle ${pinned} matches the wrapper"
COPY checkstyle checkstyle
COPY build-logic build-logic
COPY src src
RUN --mount=type=cache,id=psp-simulators-gradle,target=/gradle-home \
    gradle bootJar --no-daemon --console=plain \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /workspace/psp-simulators.jar \;

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --home-dir /app simulator
WORKDIR /app
COPY --from=build /workspace/psp-simulators.jar /app/psp-simulators.jar
USER simulator
ENV SIMULATOR_BIND_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/psp-simulators.jar"]
