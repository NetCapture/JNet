#!/bin/bash

mvn -N io.takari:maven:0.7.6:wrapper
# 指定使用版本
# mvn -N io.takari:maven:0.7.6:wrapper -Dmaven=3.3.3

echo "process"