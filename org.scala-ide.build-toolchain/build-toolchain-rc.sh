#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.1.RC2

set_version ${SCALA_VERSION}

build $*
