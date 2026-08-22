FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY services/payment-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]



