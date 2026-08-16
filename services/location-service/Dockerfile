# Build stage
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy root pom.xml and all source modules for Maven dependency resolution
COPY pom.xml .
COPY common-library ./common-library
COPY services ./services

# Build common-library and location-service
RUN mvn clean package -pl services/location-service -am -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/services/location-service/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
