#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.1
SBT_VERSION=0.10.3

PROFILE_NAME="-P local-scala-2.9.x,!scala-trunk"

build $*
