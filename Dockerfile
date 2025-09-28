# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Run stage ----
FROM eclipse-temurin:21-jre
# Add non-root user for security
RUN useradd -r -u 1001 appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
USER appuser
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
