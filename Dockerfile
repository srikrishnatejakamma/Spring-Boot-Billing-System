FROM node:22-alpine AS ui
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM gradle:8.14.3-jdk21 AS backend
WORKDIR /app
COPY --chown=gradle:gradle . .
COPY --from=ui --chown=gradle:gradle /app/frontend/dist ./frontend/dist
RUN gradle :billing-api:bootJar -x :frontend:build --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S billing && adduser -S billing -G billing && mkdir data && chown billing:billing data
COPY --from=backend /app/billing-api/build/libs/billing-api-1.0.0.jar app.jar
USER billing
ENV SERVER_ADDRESS=0.0.0.0
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
