#!/bin/bash

set -ex

exec /opt/java/bin/java $JVM_ARGS -jar /jiggler-standalone.jar "$@"
