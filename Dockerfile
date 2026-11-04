# syntax=docker/dockerfile:1
# Runtime image built from the jar produced by `./mvnw package` (build once, promote the same
# artifact). .dockerignore keeps only the jar in the build context.
#
#   ./mvnw -DskipTests package && docker build -t sarf:local .
#
# The base image is pinned by digest (reproducible builds).

FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555 AS layers
WORKDIR /layers
COPY target/sarf-*.jar app.jar
# Spring Boot layers: dependencies change rarely, application classes often -> better image caching.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
RUN addgroup -S sarf && adduser -S -G sarf -H -s /sbin/nologin sarf
WORKDIR /app
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
USER sarf
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
