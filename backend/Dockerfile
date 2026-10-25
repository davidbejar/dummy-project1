# Backend image, used by the DAST stack (compose.dast.yaml).
FROM docker.io/library/eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Tests run in their own CI jobs; this stage only packages the jar.
RUN ./mvnw -q -B package -DskipTests

FROM docker.io/library/eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /src/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
