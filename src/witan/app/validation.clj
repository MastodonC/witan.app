(ns witan.app.validation)

(defn csv-extension?
  [filename]
  (let [ext (last (clojure.string/split filename #"."))]
    (== ext "csv")))

;; TODO WIP
(defn header-line-ok?
  [file expected-header]
  (with-open [rdr (clojure.java.io/reader (:tempfile file))]
    (let [lines (line-seq rdr)]
      (first lines)))
  )
