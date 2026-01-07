# ビルドステージ
FROM maven:3.8.5-openjdk-8 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# 実行ステージ
FROM eclipse-temurin:8-jdk
WORKDIR /app

RUN apt-get update && apt-get install -y graphviz
RUN apt-get install -y fonts-noto fonts-dejavu

# json フォルダと JAR をコピー
COPY --from=build /app/json ./json
COPY --from=build /app/target/demo-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
