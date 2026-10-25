FROM node:22-alpine AS web-build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY index.html vite.config.js ./
COPY src ./src
COPY public ./public
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-21 AS java-build
WORKDIR /app

COPY backend/student-service ./backend/student-service
COPY --from=web-build /app/dist/ ./backend/student-service/src/main/resources/static/
RUN mvn -f backend/student-service/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN useradd --system --uid 10001 --create-home app
COPY --from=java-build --chown=10001:10001 \
    /app/backend/student-service/target/student-service-0.0.1-SNAPSHOT.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=production
ENV SERVER_ADDRESS=0.0.0.0
ENV SERVER_PORT=8080
EXPOSE 8080
USER 10001:10001

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
