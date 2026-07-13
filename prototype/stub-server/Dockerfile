FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

ENV TZ=Asia/Seoul
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime && \
    echo "Asia/Seoul" > /etc/timezone

COPY build/libs/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", \
    "-Xms256m", "-Xmx512m", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
