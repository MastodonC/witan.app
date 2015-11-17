(ns user
  
  ;; DO NOT ADD ANYTHING HERE THAT MIGHT REMOTELY HAVE A COMPILATION ERROR.
   ;; THIS IS TO ENSURE WE CAN ALWAYS GET A REPL STARTED.
   ;;
   ;; see (init) below.

  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [witan.application]
            [ring.adapter.jetty :as jetty]))

(defn init
  "Constructs the current development system."
  []

  ;; We do some gymnastics here to make sure that the REPL can always start
  ;; even in the presence of compilation errors.
  (require '[witan.system])
  
  (let [new-system (resolve 'witan.system/system)]
    (alter-var-root #'witan.application/system
                    (constantly (new-system)))))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'witan.application/system component/start))

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'witan.application/system
                  (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'dev/go))
