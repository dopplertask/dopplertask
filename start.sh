#!/bin/sh -x
java -XX:+ExitOnOutOfMemoryError -Dwebdriver.chrome.driver=/usr/bin/chromedriver -jar /opt/spring-boot/*.jar