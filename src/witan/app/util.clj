(ns witan.app.util
  (:require [clojure.data.json :as json]))

(defn load-extensions!
  "Adds any extensions to types that we need"
  []
  ;; java.util.UUID
  (extend java.util.UUID
    clojure.data.json/JSONWriter
    {:-write (fn [uuid out] (.print out (json/write-str (str uuid))))}))
