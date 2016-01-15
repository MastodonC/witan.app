#!/usr/bin/env bash
echo -en "This will erase the database on staging..\n\nConfirm [yN]: "

read yn

case $yn in
    [Yy]* ) ;;
    *) echo "Aborting";
       exit 1;
esac
cat <<EOF |
(require '[witan.app.db-setup :refer [load-db-schema!]])
(require '[witan.app.config :as c])
(load-db-schema! c/config)
(exit)
EOF
lein repl :connect witan-app.marathon.mesos:5001
