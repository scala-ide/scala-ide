#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.0

${MAVEN} \
  -U \
  -P local-scala-2.8.0.final,!scala-2.8.0.final \
  -Dscala.version=${SCALA_VERSION} \
  clean install $*
