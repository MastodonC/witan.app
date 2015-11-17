(ns witan.bootstrap
  "Start up for application"
  (:require [witan.application]
            [witan.system]
            [cider.nrepl :refer (cider-nrepl-handler)]
            [clojure.tools.cli          :refer [cli]]
            [clojure.tools.logging      :as log]
            [clojure.tools.nrepl.server :as nrepl-server]
            [com.stuartsierra.component :as component]))

;; See http://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
(defn install-default-exception-handler []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (log/error ex "Uncaught exception on " (.getName thread))))))

(defrecord ReplServer [config]
  component/Lifecycle
  (start [this]
    (println "Starting REPL server " config)
    (assoc this :repl-server
           (apply nrepl-server/start-server :handler cider-nrepl-handler (flatten (seq config)))))
  (stop [this]
    (println "Stopping REPL server with " config)
    (nrepl-server/stop-server (:repl-server this))
    (dissoc this :repl-server)))

(defn mk-repl-server [config]
  (ReplServer. config))

(defn build-application [opts]
  (let [system (witan.system/system)]
    (-> system
        (cond-> (:repl opts)
                (assoc :repl-server (mk-repl-server {:port (:repl-port opts)}))))))

(defn bootstrap [args]

  (let [[opts args banner]
        (cli args
             ["-h" "--help" "Show help"
              :flag true :default false]
             ["-R" "--repl" "Start a REPL"
              :flag true :default true]
             ["-r" "--repl-port" "REPL server listen port"
              :default 5001 :parse-fn #(Integer/valueOf %)])]

    (when (:help opts)
      (println banner)
      (System/exit 0))

    (install-default-exception-handler)
    
    (alter-var-root #'witan.application/system (fn [_] (component/start (build-application opts))))))
