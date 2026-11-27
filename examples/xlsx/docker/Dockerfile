FROM gradle:8.5-jdk17 AS build
WORKDIR /home/gradle/source
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon :modules:engine:jar :modules:cli:jar --console=plain

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig fonts-dejavu-core python3 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 utlx \
    && useradd --uid 10001 --gid 10001 --no-create-home utlx \
    && install -d -o utlx -g utlx /app /work
WORKDIR /app
ENV HOME=/work TMPDIR=/work
COPY --from=build /home/gradle/source/modules/engine/build/libs/utlxe-*.jar /app/utlxe.jar
COPY --from=build /home/gradle/source/modules/cli/build/libs/cli-*.jar /app/utlx.jar
COPY scripts/test-xlsx-native.py /app/test-xlsx-cli.py
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-Xms128m", "-Xmx1408m", "-XX:MaxDirectMemorySize=128m", \
    "-Djava.awt.headless=true", "-Djava.io.tmpdir=/work", "-jar", "/app/utlxe.jar"]
CMD ["--mode", "http", "--http-port", "8080", "--admin-port", "8081", "--workers", "2"]
