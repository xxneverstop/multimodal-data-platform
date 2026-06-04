FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the pre-built JAR (placed here by copy-deploy.bat)
COPY mmdp-backend.jar /app/mmdp-backend.jar

EXPOSE 19021

ENTRYPOINT ["java", "-jar", "mmdp-backend.jar"]
