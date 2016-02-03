(ns witan.system
  (:require [com.stuartsierra.component :as component]
            [witan.app.handler]
            [ring.adapter.jetty         :as jetty]
            [environ.core               :as env]
            [clojure.tools.logging      :as log]
            [witan.app.config           :as conf]
            [witan.app.components.kafka :as kafka]))

(defrecord JettyServer [handler port]
  component/Lifecycle
  (start [this]
    (log/info "Starting JettyServer")
    (assoc this ::server (jetty/run-jetty handler {:port port
                                                   :join? false})))
  (stop [this]
    (log/info "Stopping JettyServer")
    (.stop (::server this)) ;; this is the jetty shutdown fn.
    (dissoc this ::server)))

(defn system []
  (let [api-port   (or (env/env :witan-api-port) 3000)
        kafka-ip   (or (conf/kafka :host)        "localhost")
        kafka-port (or (conf/kafka :port)        9092) ]
    (component/system-map
     :jetty-server (->JettyServer #'witan.app.handler/app api-port)
     :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
     :mq           (kafka/new-kafka-producer kafka-ip kafka-port))))
