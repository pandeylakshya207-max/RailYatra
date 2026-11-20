# RailYatra web app on Render: compiles the same Java code + the web server, then runs it.
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY src src
COPY web web
RUN mkdir out-web && javac -d out-web $(find src web/src -name "*.java")
EXPOSE 8080
CMD ["java", "-cp", "out-web", "railyatra.web.WebServer"]
