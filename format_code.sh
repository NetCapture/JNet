#!/bin/bash

set -e

rm -rf google-java-format-1.12.0-all-deps.jar
# wget https://repo1.maven.org/maven2/com/google/googlejavaformat/google-java-format/1.12.0/google-java-format-1.12.0-all-deps.jar
wget https://cdn.staticaly.com/gh/hhhaiai/testAPP/master/google-java-format-1.12.0-all-deps.jar
# wget https://gitee.com/safei/metool/raw/master/google-java-format-1.12.0-all-deps.jar

if [ $# == 0 ]; then
  echo "下载成功,即将开始检查..."
  chmod -R 777 *
  git config core.filemode false
  if [ "$(uname)" == "Darwin" ]; then
    echo "==========mac os==========="
    #find . -name "*.java" -exec /Library/Java/JavaVirtualMachines/jdk-11.0.13.jdk/Contents/Home/bin/java --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED  --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED -jar ./google-java-format-1.12.0-all-deps.jar -i -r -a --skip-reflowing-long-strings --skip-javadoc-formatting {} \;
    #find . -name "*.java" -exec /Library/Java/JavaVirtualMachines/jdk-11.0.13.jdk/Contents/Home/bin/java  -jar ./google-java-format-1.12.0-all-deps.jar -i -r -a  --fix-imports-only --skip-reflowing-long-strings --skip-javadoc-formatting  {} \;
    find . -name "*.java" -exec java  -jar ./google-java-format-1.12.0-all-deps.jar -i -r -a  --fix-imports-only --skip-reflowing-long-strings --skip-javadoc-formatting  {} \;
  else
    echo "==========linux os==========="
    find . -name "*.java" -exec java --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED  --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED -jar ./google-java-format-1.12.0-all-deps.jar -i -r -a --skip-reflowing-long-strings --skip-javadoc-formatting {} \;
  fi
fi

rm -rf google-java-format-1.12.0-all-deps.jar

### os check
#  if["$(uname)"=="Darwin"];then
#  # Mac OS X 操作系统
#  elif["$(expr substr $(uname -s) 1 5)"=="Linux"];then
#  # GNU/Linux操作系统
#  elif["$(expr substr $(uname -s) 1 10)"=="MINGW32_NT"];then
#  # Windows NT操作系统
#  fi
