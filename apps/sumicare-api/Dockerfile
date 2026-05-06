FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY apps/sumicare-api/pom.xml ./pom.xml
RUN mvn -B -e -ntp dependency:go-offline
COPY apps/sumicare-api/src ./src
RUN mvn -B -e -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=builder /workspace/target/sumicare-api.jar /app/app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
