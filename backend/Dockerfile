FROM node:22-alpine@sha256:0e1754758c2df4cfa3e68d59e2e1b7374bb7fd9ea6328eaaacf15cc934af8202 AS web
WORKDIR /workspace
COPY . .
RUN mkdir /web && if [ -f frontend/package.json ]; then \
      cd frontend && npm ci && npm run build && cp -a dist/. /web/; \
    else cp -a backend/src/main/resources/static/. /web/; fi

FROM maven:3.9.9-eclipse-temurin-21-jammy AS build
WORKDIR /workspace/backend
COPY backend/pom.xml .
COPY backend/src src
COPY --from=web /web/ src/main/resources/static/
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM maven:3.9.9-eclipse-temurin-21-jammy AS runtime
WORKDIR /app
COPY --from=build /workspace/backend/target/youdo-backend-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
