FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /builder

ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-noble AS runtime

LABEL org.opencontainers.image.title="Feature Flag Service"
LABEL org.opencontainers.image.description="REST API for managing application feature flags"

WORKDIR /application

RUN groupadd --system appgroup \
 && useradd  --system --gid appgroup --no-create-home appuser \
 && mkdir -p /application/data \
 && chown -R appuser:appgroup /application

COPY --from=builder --chown=appuser:appgroup /builder/extracted/dependencies/         ./
COPY --from=builder --chown=appuser:appgroup /builder/extracted/spring-boot-loader/   ./
COPY --from=builder --chown=appuser:appgroup /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /builder/extracted/application/          ./

USER appuser

VOLUME /application/data

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-jar", "application.jar"]
