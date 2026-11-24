# Server image. The fat JAR is built beforehand by `./gradlew :server:shadowJar` (the release workflow does this
# after the gate), so the image only packages it: no Gradle or Android SDK inside the build.
FROM eclipse-temurin:25-jre

LABEL org.opencontainers.image.source="https://github.com/drusin/kid-screen-time" \
      org.opencontainers.image.description="Bildschirmzeit server"

WORKDIR /app
COPY server/build/libs/kid-screen-time-server.jar /app/kid-screen-time-server.jar

# Public API port and admin port (publish the admin port only to the home network). The compose file runs the
# container as 1000:1000 with a bind mount on /data.
ENV DATABASE_PATH=/data/kid-screen-time.db
EXPOSE 8080 8081

ENTRYPOINT ["java", "-jar", "/app/kid-screen-time-server.jar"]
