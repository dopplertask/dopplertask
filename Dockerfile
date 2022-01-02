FROM openjdk:17-alpine AS jlink-package
# First: generate java runtime module by jlink.

RUN jlink \
     --module-path /opt/openjdk-17/jmods \
     --compress=2 \
     --add-modules jdk.jfr,jdk.management.agent,java.base,java.logging,java.xml,jdk.unsupported,java.sql,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,java.net.http,jdk.crypto.ec  \
     --no-header-files \
     --no-man-pages \
     --output /opt/jdk-17-mini-runtime

ADD . /root

RUN cd ~/backend && chmod +x gradlew && ./gradlew build -x test && chmod +x /root/start.sh && chmod +x /root/setup.sh


# Second image


# Third image
FROM alpine:3.10

MAINTAINER Feras Wilson, http://www.dopplertask.com

ENV JAVA_HOME=/opt/jdk-17-mini-runtime
ENV PATH="$PATH:$JAVA_HOME/bin"

RUN apk add chromium chromium-chromedriver

COPY --from=jlink-package /root/start.sh /opt/spring-boot/
COPY --from=jlink-package /root/backend/build/libs/doppler-*.jar /opt/spring-boot/
COPY --from=jlink-package /opt/jdk-17-mini-runtime /opt/jdk-17-mini-runtime

RUN mkdir /opt/spring-boot/bin && cd /opt/spring-boot/bin && ln /usr/lib/chromium/chromedriver chromedriver


EXPOSE 8090
EXPOSE 61617
WORKDIR  /opt/spring-boot/
CMD ["/opt/spring-boot/start.sh"]
