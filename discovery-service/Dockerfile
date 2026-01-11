# Stage 1: Creacion del Jar
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build
# Toma el pom del padre y el del microservicio
COPY pom.xml .
COPY discovery-service/pom.xml discovery-service/pom.xml

RUN mvn -pl discovery-service -am dependency:go-offline

COPY discovery-service/src discovery-service/src

RUN mvn -pl discovery-service -am clean package -DskipTests

# Stage 2: Imagen Final con solo el jar para levantar
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /build/discovery-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
