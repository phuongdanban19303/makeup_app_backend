FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY services/booking-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
