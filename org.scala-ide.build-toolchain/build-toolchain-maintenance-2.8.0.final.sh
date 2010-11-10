#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.0
SCALA_MAINTENANCE_VERSION=2.8.0.final-maintenance

set_version ${SCALA_VERSION}

${MAVEN} \
  -U \
  -P maintenance,!default \
  -Dscala.version=${SCALA_VERSION} \
  -Dscala.maintenance.version=${SCALA_MAINTENANCE_VERSION} \
  clean install $* 
