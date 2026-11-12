FROM alpine:3.24 AS build
RUN apk add openjdk25-jdk openjdk25-jmods
ENV PATH="/usr/lib/jvm/java-25-openjdk/bin:$PATH"
COPY . /src
RUN /src/bundle.sh

FROM alpine:3.24
LABEL maintainer="Feras Wilson, https://www.dopplertask.com"
RUN apk add chromium chromium-chromedriver
COPY --from=build /src/dist/dopplertask /opt/dopplertask
ENV JAVA_OPTS="-Dwebdriver.chrome.driver=/usr/bin/chromedriver"
EXPOSE 8090
EXPOSE 61617
WORKDIR /opt/dopplertask
CMD ["/opt/dopplertask/bin/dopplertask"]
