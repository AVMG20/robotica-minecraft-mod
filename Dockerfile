# Build environment for Robotica: JDK 21, nothing else. The source is mounted, not copied,
# so the jar lands in build/libs/ on your machine. Use scripts/docker-build.sh.
FROM eclipse-temurin:21-jdk
RUN apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*
ENV GRADLE_USER_HOME=/gradle
WORKDIR /work
ENTRYPOINT ["./gradlew", "--no-daemon", "--console=plain"]
CMD ["build"]
