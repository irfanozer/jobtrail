FROM node:24-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace/backend
COPY backend/pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY backend/src/ ./src/
COPY --from=frontend-build /workspace/frontend/dist/ ./src/main/resources/static/
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 jobtrail \
    && useradd --uid 10001 --gid jobtrail --no-create-home --shell /usr/sbin/nologin jobtrail
WORKDIR /app
COPY --from=backend-build --chown=jobtrail:jobtrail /workspace/backend/target/jobtrail-*.jar /app/app.jar
ENV SERVER_ADDRESS=0.0.0.0 \
    PORT=8080 \
    SERVER_TOMCAT_THREADS_MAX=32 \
    SERVER_TOMCAT_THREADS_MIN_SPARE=2 \
    SERVER_TOMCAT_MAX_CONNECTIONS=100 \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"
USER jobtrail
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=3 \
    CMD curl --fail --silent --output /dev/null "http://127.0.0.1:${PORT:-8080}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
