FROM eclipse-temurin:21-jre

# Install required libraries for RocksDB
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    libstdc++6 \
    libgcc1 \
    zlib1g \
    libc6-dev \
    libnuma1 && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY target/*.jar app.jar

EXPOSE 18282

# Set Java options for native library loading
ENV JAVA_OPTS="-Djava.library.path=/app:/app/BOOT-INF/lib -Dorg.rocksdb.sharedLibraryPath=/app"

# Run with explicit library path options
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
