# syntax=docker/dockerfile:1

# ---- Build: compila o jar com o Gradle wrapper (JDK 25) ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependências primeiro, para aproveitar o cache de camadas quando só o código muda
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Runtime: só o JRE + o jar ----
# Imagem baseada em Ubuntu (glibc), necessária para as bibliotecas nativas do JDAVE (DAVE)
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system sabadaco && useradd --system --gid sabadaco sabadaco
COPY --from=build /workspace/build/libs/sabadaco.jar app.jar
USER sabadaco

EXPOSE 8080
# --enable-native-access: o JDAVE usa a API de FFM (código nativo) do Java 25
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
