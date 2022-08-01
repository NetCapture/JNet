#!/bin/bash
#brew install dos2unix
#sudo apt-get update
#sudo apt-get install dos2unix
find $PWD -type f -exec dos2unix {} \;
