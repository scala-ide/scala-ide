#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.0

${MAVEN} \
  -U \
  -P scala-2.8.0-maintenance,!scala-2.8.0 \
  -Dscala.version=${SCALA_VERSION} \
  clean install $*
