#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.0

set_version ${SCALA_VERSION}

build $*
