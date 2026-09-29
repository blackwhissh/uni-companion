FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/learning-service/pom.xml services/learning-service/pom.xml
COPY services/identity-service services/identity-service
RUN mvn -B -pl services/identity-service -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
