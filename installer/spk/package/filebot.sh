#!/bin/sh
PRG="$0"

# resolve relative symlinks
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG="`dirname "$PRG"`/$link"
  fi
done

# make it fully qualified
WORKING_DIR=`pwd`
PRG_DIR=`dirname "$PRG"`
APP_ROOT=`cd "$PRG_DIR" && pwd`
APP_DATA="$APP_ROOT/data"

# restore original working dir
cd "$WORKING_DIR"


# add APP_ROOT to LD_LIBRARY_PATH
if [ ! -z "$LD_LIBRARY_PATH" ]
then
  export LD_LIBRARY_PATH="$APP_ROOT:$LD_LIBRARY_PATH"
else
  export LD_LIBRARY_PATH="$APP_ROOT"
fi

# force JVM language and encoding settings
export LANG=en_US.utf8

java -Djava.awt.headless=true -Dunixfs=false -DuseExtendedFileAttributes=true -DuseCreationDate=false -Dfile.encoding=UTF-8 -Djava.net.useSystemProxies=true -Dsun.net.client.defaultConnectTimeout=10000 -Dsun.net.client.defaultReadTimeout=60000 -Djna.nosys=true -Dapplication.deployment=spk -Dapplication.analytics=true "-Dnet.filebot.AcoustID.fpcalc=fpcalc" "-Dapplication.dir=$APP_DATA" "-Djava.io.tmpdir=$APP_DATA/temp" "-Duser.home=$APP_DATA" -Djava.util.prefs.PreferencesFactory=net.filebot.util.prefs.FilePreferencesFactory "-Dnet.filebot.util.prefs.file=$APP_DATA/prefs.properties" -jar "$APP_ROOT/FileBot.jar" "$@"