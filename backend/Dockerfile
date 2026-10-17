FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S trendify && adduser -S trendify -G trendify
USER trendify
COPY --from=build /src/target/trendify-backend-1.0.0.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
