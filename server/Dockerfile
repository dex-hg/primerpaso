FROM eclipse-temurin:21.0.13_9-jdk-noble AS compilacion
WORKDIR /compilacion
COPY pom.xml mvnw ./
COPY .mvn/ .mvn/
RUN sh mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN sh mvnw -B -ntp package && cp target/primerpaso-*.jar /primerpaso.jar

FROM eclipse-temurin:21.0.13_9-jre-noble
WORKDIR /opt/primerpaso
ENV SPRING_PROFILES_ACTIVE=supabase,render
COPY --from=compilacion /primerpaso.jar ./primerpaso.jar
USER 10001:10001
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/opt/primerpaso/primerpaso.jar"]
