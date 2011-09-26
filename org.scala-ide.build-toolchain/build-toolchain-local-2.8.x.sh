#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.2-SNAPSHOT

set_version ${SCALA_VERSION}

PROFILE="-P local"

build $*
