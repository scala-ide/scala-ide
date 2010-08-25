#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.0-SNAPSHOT

${MAVEN} \
  -U \
  -P local-scala-2.8.0.trunk,!scala-2.8.0.trunk \
  -Dscala.version=${SCALA_VERSION} \
  clean install $*
