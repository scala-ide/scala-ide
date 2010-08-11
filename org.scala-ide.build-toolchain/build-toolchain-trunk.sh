#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.0-SNAPSHOT

set_version ${SCALA_VERSION}

build $*
