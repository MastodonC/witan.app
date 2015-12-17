#!/usr/bin/env bash
cat <<EOF |
(require '[witan.app.db-setup :refer [load-db-schema!]])
(require '[witan.app.config :as c])
(load-db-schema! c/config)
(exit)
EOF
lein repl


