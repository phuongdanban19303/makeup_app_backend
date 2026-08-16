# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy root pom.xml and all source modules for Maven dependency resolution
COPY pom.xml .
COPY common-library ./common-library
COPY services ./services

# Build common-library and api-gateway
RUN mvn clean package -pl services/api-gateway -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/services/api-gateway/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
