#!/bin/sh

source $(dirname $0)/env.sh

SCALA_TOOLCHAIN_VERSION=2.8.0-trunk
SCALA_VERSION=2.8.0.trunk

${MAVEN} \
-Dscala.toolchain.version=${SCALA_TOOLCHAIN_VERSION} \
-Dscala.version=${SCALA_VERSION} \
-f toolchain-pom.xml clean install $* && \
${MAVEN} \
-Dscala.toolchain.version=${SCALA_TOOLCHAIN_VERSION} \
-Dscala.version=${SCALA_VERSION} \
clean install
