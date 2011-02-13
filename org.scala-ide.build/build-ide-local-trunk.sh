#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.0-SNAPSHOT

${MAVEN} \
  -U \
  -P local-scala-trunk,!scala-trunk \
  -Dscala.version=${SCALA_VERSION} \
  clean install $*
