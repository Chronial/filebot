#!/bin/bash
java -Dapplication.deployment=deb -Djna.library.path=/usr/share/filebot -Xmx256m -jar /usr/share/filebot/FileBot.jar "$@"