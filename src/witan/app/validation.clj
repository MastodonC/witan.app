(ns witan.app.validation
  (:require [clojure.tools.logging :as log]
            [qbits.hayt :as hayt]
            [witan.app.config :as c])
  (:use [liberator.core :only [defresource]]))

(defn find-validation
  [category]
  (hayt/select :validations (hayt/where {:category category})))

(defn get-validation
  [category]
  (first (c/exec (find-validation category))))

(defn add-validation!
  [{:keys [category header-row]}]
  (c/exec (hayt/insert :validations (hayt/values {:category category
                                                  :header_row header-row}))))
(defn csv-extension?
  [filename]
  (let [ext (last (clojure.string/split filename #"\."))]
    (= ext "csv")))

(defn header-line-ok?
  [header expected-header]
  (= header expected-header))

(defn validate-content
  [category file output-function]
  (if-let [validation (get-validation category)]
    (with-open [rdr (clojure.java.io/reader file)]
      (let [lines (line-seq rdr)]
        (log/info (first lines))
        [(header-line-ok? (first lines) (:header_row validation))
         {:validation-error (str "The header row of the file is incorrect. we expect " (:header_row validation))}]
)))
  [nil {:validation-error "Could not find the validation for this data category."}])

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
