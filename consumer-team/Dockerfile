FROM maven:3.9.6-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:11-jre
WORKDIR /app
COPY --from=build /app/target/consumer-team-1.0.0.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
