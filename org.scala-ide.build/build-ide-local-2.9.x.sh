#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.0-SNAPSHOT
PROFILE_NAME="-P local-scala-2.9.x,!scala-trunk"

build $*