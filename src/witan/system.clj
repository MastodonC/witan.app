(ns witan.system
  (:require [com.stuartsierra.component :as component]
            [witan.app.handler]
            [ring.adapter.jetty :as jetty]))

(defrecord JettyServer [handler port]
    component/Lifecycle
  (start [this]
    (println "Starting JettyServer")
    (assoc this ::server (jetty/run-jetty handler {:port port
                                                   :join? false})))
  (stop [this]
    (println "Stopping JettyServer")
    ((::server this)) ;; this is the jetty shutdown fn.
    (dissoc this ::server)))

(defn system []
  (-> (component/system-map
       :jetty-server (->JettyServer witan.app.handler/app 3000)
       :repl-server  (Object.) ; dummy - replaced when invoked via uberjar.   
       )))

