# Stage 1: build (JDK + Maven)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copies dependencies first
COPY pom.xml .
# Cache them in this layer
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Stage 2: explode the fat JAR into layers
FROM eclipse-temurin:21-jre AS extract
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# Stage 3: runtime (JRE only)
FROM eclipse-temurin:21-jre
WORKDIR /app
# Order matters: least-changing first, so Docker caching works
COPY --from=extract /app/dependencies/ ./
COPY --from=extract /app/spring-boot-loader/ ./
COPY --from=extract /app/snapshot-dependencies/ ./
COPY --from=extract /app/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
