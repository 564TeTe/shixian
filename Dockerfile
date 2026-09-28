FROM node:22-bookworm-slim AS frontend
WORKDIR /build/front
COPY front/package.json front/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY front/ ./
ENV NODE_OPTIONS=--openssl-legacy-provider
# Invoke Vue CLI directly: the repository's npm scripts use Windows `set`.
RUN node node_modules/@vue/cli-service/bin/vue-cli-service.js build

FROM maven:3.9-eclipse-temurin-8 AS backend
WORKDIR /build/back
COPY back/pom.xml ./
COPY back/src/main/ ./src/main/
# The local application.yml is excluded from the Docker build context.
COPY deploy/render/application.yml ./src/main/resources/application.yml
COPY --from=frontend /build/front/dist/ ./src/main/resources/static/
RUN mvn -B -ntp -Dproject.build.sourceEncoding=UTF-8 -Dmaven.test.skip=true package

FROM eclipse-temurin:8-jre-jammy AS runtime
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -Xss512k -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"
COPY --from=backend /build/back/target/springboote51e2-0.0.1-SNAPSHOT.jar ./application.jar
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
