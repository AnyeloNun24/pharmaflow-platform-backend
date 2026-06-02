# Stage 1: Creacion del Jar
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY . .
RUN mvn -pl auth-service -am clean package -DskipTests

# Stage 2: Imagen Final con solo el jar para levantar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/auth-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]