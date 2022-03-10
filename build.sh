#!/bin/bash

export GPG_TTY=$(tty)

fileName="$0"
#https://issues.sonatype.org/browse/OSSRH-66257
export MAVEN_OPTS="--add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.desktop/java.awt.font=ALL-UNNAMED"
if [ "$1" == "deploy" ]; then
  mvn clean deploy
elif [ "$1" == "upload" ]; then
  mvn clean deploy
else
  mvn clean install
fi
