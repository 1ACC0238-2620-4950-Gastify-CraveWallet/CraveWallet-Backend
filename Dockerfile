FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
COPY src src
RUN ./mvnw -B verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system cravewallet && useradd --system --gid cravewallet cravewallet
COPY --from=build --chown=cravewallet:cravewallet /build/target/cravewallet-backend-0.1.0-SNAPSHOT.jar app.jar
USER cravewallet
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
