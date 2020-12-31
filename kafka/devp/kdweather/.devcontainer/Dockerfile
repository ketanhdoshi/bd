#FROM centos:7
FROM confluentinc/cp-kafka-connect-base:6.0.0

# Temporarily switch to root user to install JDK/Maven
USER root

# Kafka Connect base image already has a JDK so point to it. No need to install.
ENV JAVA_HOME=/usr/lib/jvm/zulu11

# ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

# RUN yum install -y tzdata openssl curl ca-certificates fontconfig gzip tar \
#     && yum update -y; yum clean all

# ENV JAVA_VERSION jdk-11.0.9.1+1

# COPY slim-java* /usr/local/bin/

# RUN set -eux; \
#     ESUM='e388fd7f3f2503856d0b04fde6e151cbaa91a1df3bcebf1deddfc3729d677ca3'; \
#     BINARY_URL='https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.9.1%2B1/OpenJDK11U-jdk_x64_linux_hotspot_11.0.9.1_1.tar.gz'; \
#     curl -LfsSo /tmp/openjdk.tar.gz ${BINARY_URL}; \
#     echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
#     mkdir -p /opt/java/openjdk; \
#     cd /opt/java/openjdk; \
#     tar -xf /tmp/openjdk.tar.gz --strip-components=1; \
#     rm -rf /tmp/openjdk.tar.gz;

# ENV JAVA_HOME=/opt/java/openjdk \
#     PATH="/opt/java/openjdk/bin:$PATH"

ARG MAVEN_VERSION="3.6.3"
ARG USER_HOME_DIR="/root"
ARG SHA="c35a1803a6e70a126e80b2b3ae33eed961f83ed74d18fcd16909b2d44d7dada3203f1ffe726c17ef8dcca2dcaa9fca676987befeadc9b9f759967a8cb77181c0"
ARG BASE_URL="https://downloads.apache.org/maven/maven-3/${MAVEN_VERSION}/binaries"

RUN mkdir -p /usr/share/maven \
        && curl -Lso  /tmp/maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
        # && echo "${SHA} */tmp/maven.tar.gz" | sha256sum -c - \
        && tar -xzC /usr/share/maven --strip-components=1 -f /tmp/maven.tar.gz \
        && rm -v /tmp/maven.tar.gz \
        && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "${USER_HOME_DIR}/.m2"

# Revert back to user needed for Kafka Connect
USER appuser