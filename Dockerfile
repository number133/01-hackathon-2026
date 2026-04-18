# syntax=docker/dockerfile:1.7

# Stage 1 — build Angular frontend.
FROM node:20-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install --no-audit --no-fund; fi
COPY frontend/ ./
RUN npm run build -- --configuration=production

# Stage 2 — build Spring Boot jar with Angular baked in as static resources.
FROM gradle:8.10-jdk21 AS api
WORKDIR /api
COPY backend/ ./
COPY --from=web /web/dist/chat-frontend/browser/ src/main/resources/static/
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test

# Stage 3 — runtime.
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=api /api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
