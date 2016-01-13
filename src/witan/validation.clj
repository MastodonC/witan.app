(ns witan.validation
  (:require [clojure.string :as str]))

(def development-data-category "development-data")

(defn exists?
  [category]
  (some #{category} [development-data-category]))

(defn csv-extension?
  [filename]
  (let [ext (last (clojure.string/split filename #"\."))]
    (= ext "csv")))

(defn all-years-starting-from-2011
  [year-headers]
  (let [years (sort (map #(Long/parseLong (re-find #"\d{4}" %)) year-headers))]
    (= (vec (range 2011 (inc (last years))))
        years)))

(defn category-validation-error?
  [category lines]
  (let [req-headers #{"gss.code.borough" "gss.code.ward" "ward.name"}
        inc-headers (str/split (first lines) #",")
        formatted-headers (->> inc-headers
                               (map str/lower-case)
                               (map #(str/replace % #"\s" "."))
                               (map #(str/replace % #"\"" ""))
                               (set))
        year-headers (filter #(re-find #"\d{4}-\d{4}" %) inc-headers)]
    (cond
      (not (empty? (clojure.set/difference req-headers formatted-headers)))
      (str "The header row of the file is missing one or more required rows. We expect " (str/join "," req-headers))
      (not (all-years-starting-from-2011 year-headers))
      (str "Years should start at 2011-2012 and all be present up to the year to be projected to.")
      :else nil)))

(defn validate
  [category file]
  (with-open [rdr (clojure.java.io/reader file)]
    (let [lines (line-seq rdr)
          error? (category-validation-error? category lines)]
      [(nil? error?) (when error? {:error error?})])))

(defn validate-content
  [category file]
  (if (exists? category)
    (validate category file)
    [false {:error "Could not find the validation for this data category."}]))
