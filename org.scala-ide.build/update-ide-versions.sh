#!/bin/sh

. $(dirname $0)/env.sh


SCALA_TOOLCHAIN_VERSION=2.8.0
${MAVEN} -f toolchain-pom.xml -N versions:set -DnewVersion=${SCALA_TOOLCHAIN_VERSION}
${MAVEN} -f toolchain-pom.xml -N versions:update-child-modules

