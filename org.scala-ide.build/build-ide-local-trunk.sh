#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.1-SNAPSHOT

${MAVEN} \
  -U \
  -P local-scala-2.8.1.trunk,!scala-2.8.1.trunk \
  -Dscala.version=${SCALA_VERSION} \
  clean install $*
