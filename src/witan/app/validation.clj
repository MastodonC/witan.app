(ns witan.app.validation
  (:require [clojure.tools.logging :as log])
  (:use [liberator.core :only [defresource]]))

(defn csv-extension?
  [filename]
  (let [ext (last (clojure.string/split filename #"\."))]
    (log/info ext)
    (= ext "csv")))

(defn header-line-ok?
  [header expected-header]
  (= ["GSS.Code.Borough","BOROUGH","Gender","GSS.Code.Ward","ZONELABEL","Age","Rate"] expected-header)
)

(defn validate-content
  [category file]
  (with-open [rdr (clojure.java.io/reader file)]
    (let [lines (line-seq rdr)]
      (header-line-ok? (first lines) "")
      (log/info (first lines))))
  true)

(defresource validation [{:keys [category file]}]
  :allowed-methods #{:post}
  :available-media-types ["application/json"]
  :processable? (fn [ctx]
                  (log/info "IN PROCESSABLE" file)
                  (if (csv-extension? (:filename file))
                    (validate-content category (:tempfile file))
                    [nil {:validation-error "this doesn't seem to be a CSV file"}]))
  :handle-unprocessable-entity (fn [ctx] {:error (:validation-error ctx)})
  :handle-created (fn [ctx]
               (log/info "you are here" (:tempfile file))
               {:boo "yeah"}))
