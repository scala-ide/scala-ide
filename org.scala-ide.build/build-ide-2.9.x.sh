#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.3-SNAPSHOT

PROFILE="-P scala-2.9.x"

build $*
