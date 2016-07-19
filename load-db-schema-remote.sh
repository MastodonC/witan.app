#!/usr/bin/env bash
PUBLIC_SLAVE_IP=$1
echo -en "This will erase the database from $PUBLIC_SLAVE_IP..\n\nConfirm [yN]: "

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
lein repl :connect $PUBLIC_SLAVE_IP:5001
