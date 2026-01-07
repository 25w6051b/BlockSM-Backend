# ビルドステージ
FROM maven:3.8.5-openjdk-8 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# 実行ステージ
FROM eclipse-temurin:8-jdk
WORKDIR /app
COPY --from=build /app/json ./json
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

