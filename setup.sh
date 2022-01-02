#!/bin/sh -x

apk add --no-cache go git libc-dev

echo 'export GO111MODULE=on' >> ~/.bashrc
source ~/.bashrc

## Compile, and make it executable directly as: doppler
export GO111MODULE=on && cd /opt/spring-boot/cli && go build && mv /opt/spring-boot/cli/cli /bin/doppler