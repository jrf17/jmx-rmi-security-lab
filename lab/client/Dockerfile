# Build stage: compile the verification client from the Maven reactor.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY lab/pom.xml pom.xml
COPY lab/client/pom.xml client/pom.xml
COPY lab/client/src client/src
COPY lab/application/pom.xml application/pom.xml
COPY lab/application/src application/src
RUN mvn -q -pl client -am package -DskipTests

# Runtime stage: minimal JRE, non-root user.
FROM eclipse-temurin:21-jre
RUN useradd --create-home --uid 10001 --shell /usr/sbin/nologin lab
USER lab
WORKDIR /app
COPY --from=build /build/client/target/jmx-rmi-verifier.jar /app/verifier.jar
ENTRYPOINT ["java", "-jar", "/app/verifier.jar"]
