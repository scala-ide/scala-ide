#!/bin/sh

source $(dirname $0)/env.sh

SCALA_TOOLCHAIN_VERSION=2.8.0-RC6
SCALA_VERSION=2.8.0.RC6

${MAVEN} \
-Dscala.toolchain.version=${SCALA_TOOLCHAIN_VERSION} \
-Dscala.version=${SCALA_VERSION} \
-f toolchain-pom.xml clean install $* && \
${MAVEN} \
-Dscala.toolchain.version=${SCALA_TOOLCHAIN_VERSION} \
-Dscala.version=${SCALA_VERSION} \
clean install $*
