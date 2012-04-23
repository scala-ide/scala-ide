#!/bin/sh

. $(dirname $0)/env.sh

SCALA_VERSION=2.9.2

PROFILE="-P scala-2.9.x"

build $*
