# Build

* mvn clean deploy
* gpg error `signing failed: Inappropriate ioctl for device`
    * shell run `export GPG_TTY=$(tty)`