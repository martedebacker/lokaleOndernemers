# ─── Stage 2: Build Spring Boot JAR (with frontend bundled in) ────────────────
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /app/backend

# Cache dependencies before copying source
COPY pom.xml ./
RUN mvn dependency:go-offline -q

COPY src ./src

# The tests run in cicd.sh before the build, not here under the arm64 emulation.
RUN mvn package -DskipTests -q

# ─── Stage 3: Minimal runtime image ──────────────────────────────────────────
ARG TARGETPLATFORM
#FROM --platform=$TARGETPLATFORM eclipse-temurin:21-jre
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=backend-build /app/backend/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
