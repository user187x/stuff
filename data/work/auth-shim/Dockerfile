# ---- Stage 1: Build the Jar ----
FROM maven:3.9-eclipse-temurin-26-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn clean
RUN mvn -B -q dependency:go-offline
RUN mvn -B -q package -DskipTests

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:26-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/target/auth-shim.jar /app/auth-shim.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/auth-shim.jar"]
