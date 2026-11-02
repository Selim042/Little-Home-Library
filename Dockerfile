# Stage 1: Use official Maven image to compile code - completely independent of your local wrappers
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

# Copy your standard project structural manifests
COPY pom.xml .

# Download and cache your dependencies natively inside the container canvas
RUN mvc dependency:go-offline -B || mvn dependency:resolve

# Copy your actual Java source code files down to the building workspace boundaries
COPY src/ ./src/
RUN mvn clean package -DskipTests

# Stage 2: Deploy compiled executable runner container
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Establish non-root process tracking limits for secure runtime execution bounds
RUN groupadd -g 1001 librarygroup && useradd -r -u 1001 -g librarygroup libraryuser

# Copy over compiled artifact out of the workspace build canvas stages
COPY --from=build /app/target/*.jar app.jar

# Setup local application volume directory target references matching code definitions
RUN mkdir -p /app/data && chown -R libraryuser:librarygroup /app

USER libraryuser
EXPOSE 8080

# Configure memory constraints optimized for low-footprint single microservices execution bounds
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xmx512m", "-jar", "app.jar"]
