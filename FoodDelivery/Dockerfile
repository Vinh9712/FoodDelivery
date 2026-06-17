# syntax=docker/dockerfile:1
# ----------------------------------------------------------------------------
# Shared multi-stage build for every executable Spring Boot module.
#
# The whole Maven reactor is built ONCE in the `build` stage. Because that stage
# does not depend on the MODULE arg, BuildKit builds it a single time and reuses
# the cached layers for every service image. Each final image then copies only
# its own module's executable jar, selected via the MODULE build arg.
#
#   docker build --build-arg MODULE=services/order-service -t order-service .
# ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the full reactor (parent pom + all module sources) and build everything.
COPY pom.xml ./
COPY shared ./shared
COPY infra ./infra
COPY services ./services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests clean package

# ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
# Path of the module to run, e.g. "services/order-service" or "infra/api-gateway".
ARG MODULE
WORKDIR /app

# curl is used by the compose healthchecks to probe /actuator/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Spring Boot repackages the executable jar as <artifactId>-<version>.jar; the
# non-executable original keeps a .jar.original extension, so this glob matches
# exactly one file.
COPY --from=build /build/${MODULE}/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
