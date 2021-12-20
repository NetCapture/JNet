#!/bin/bash

export GPG_TTY=$(tty)

fileName="$0"

if [ "$1" == "deploy" ]; then
  mvn clean deploy
elif [ "$1" == "upload" ]; then
  mvn clean deploy
else
  mvn clean install
fi
