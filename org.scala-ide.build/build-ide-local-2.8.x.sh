#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.8.2-SNAPSHOT

PROFILE_NAME="-P local-scala-2.8.1,!scala-trunk"

build $*
