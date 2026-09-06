# =============================================================
# Quiz Multijugador — Dockerfile (multi-etapa)
# Etapa 1: compila la aplicación con Maven + Temurin 21
# Etapa 2: imagen de ejecución mínima con Temurin 21 JRE
# =============================================================

# ---- Etapa de compilación ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Primero las dependencias para aprovechar la caché de capas de Docker
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Después el código fuente
COPY src ./src
RUN mvn -B -DskipTests package

# ---- Etapa de ejecución ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Quarkus genera target/quarkus-app con esta estructura
COPY --from=build /workspace/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/target/quarkus-app/*.jar ./
COPY --from=build /workspace/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

# La configuración (URL de PostgreSQL, etc.) llega por variables de entorno
# definidas en compose.yaml
CMD ["java", "-jar", "quarkus-run.jar"]
