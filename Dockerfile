# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -e -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
ENV APP_HOME=/opt/app
WORKDIR $APP_HOME
# non-root user for security
RUN useradd -r -s /bin/false appuser
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
USER appuser
# JVM tunings for container
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/urandom"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
