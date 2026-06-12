# Stage 1: build
# glibc-based image required: the protoc and protoc-gen-grpc-java binaries
# downloaded by protobuf-maven-plugin are dynamically linked against glibc
# and do not run on musl (Alpine).
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /app

RUN apt-get update \
    && apt-get install --no-install-recommends -y maven \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml ./
RUN mvn dependency:go-offline -B -q

COPY src ./src
RUN mvn package -DskipTests -B -q

# Stage 2: runtime
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup --system --gid 1001 app \
    && adduser --system --uid 1001 --ingroup app app

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

USER app

EXPOSE 3000 50051

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://localhost:3000/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
