# Stage 1: Build Java .jar from source
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -pl services/api-gateway -am -DskipTests

# Stage 2: Run application JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/services/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]


