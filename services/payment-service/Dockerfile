# Stage 1: Build Java .jar from source
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build
COPY pom.xml .
COPY common-library ./common-library
COPY services/payment-service ./services/payment-service
RUN mvn clean package -pl services/payment-service -am -DskipTests

# Stage 2: Run application JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /build/services/payment-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]

