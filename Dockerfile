# syntax=docker/dockerfile:1
#
# Dockerfile — multi-stage build for the Spring Boot app.
#
# Stage 1 (build): uses Maven to compile and package the fat JAR.
# Stage 2 (runtime): copies only the JAR into a lean JRE image.
# This keeps the final image small (~250 MB vs ~600 MB with a full JDK).
#
# Usage (after docker-compose up -d for MySQL):
#   docker build -t scim-service .
#   docker run -p 8080:8080 --network host scim-service
#   OR connect it to the same docker-compose network.

# ---- Stage 1: build ----
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
# Copy POM first so Maven dependency layer is cached
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Now copy source and build
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy the fat JAR from the build stage
COPY --from=build /app/target/scim-provisioning-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
