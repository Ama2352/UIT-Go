# === Stage 1: Build the app with Maven ===
FROM maven:3.9.4-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests
# ===== STAGE 2: RUNTIME =====
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Copy JAR file từ stage build
COPY --from=builder /app/target/*.jar app.jar

# Expose port 8081
EXPOSE 8081

# Run Spring Boot app
ENTRYPOINT ["java","-jar","app.jar"]