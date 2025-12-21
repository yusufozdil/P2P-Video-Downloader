FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/p2p-streaming-app-1.0-SNAPSHOT.jar app.jar
# Install VLC runtime for Linux
RUN apt-get update && apt-get install -y vlc && rm -rf /var/lib/apt/lists/*
EXPOSE 50000
ENTRYPOINT ["java", "-jar", "app.jar"]
