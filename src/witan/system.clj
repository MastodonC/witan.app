(ns witan.system
  (:require [com.stuartsierra.component :as component]
            [witan.app.handler]
            [ring.adapter.jetty :as jetty]
            [environ.core :as env]))

(defrecord JettyServer [handler port]
    component/Lifecycle
  (start [this]
    (println "Starting JettyServer")
    (assoc this ::server (jetty/run-jetty handler {:port port
                                                   :join? false})))
  (stop [this]
    (println "Stopping JettyServer")
    (.stop (::server this)) ;; this is the jetty shutdown fn.
    (dissoc this ::server)))

(defn system []
  (let [port (or (env/env :witan-api-port) 3000)]
    (-> (component/system-map
         :jetty-server (->JettyServer #'witan.app.handler/app port)
         :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.
         ))))
