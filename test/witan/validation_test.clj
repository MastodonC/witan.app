(ns witan.validation-test
  (:require [witan.validation :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]))

(defn validation-error
  [validated]
  (-> validated second :error))

(deftest content-validation-test

  (testing "no validation found"
    (let [validated (validate-content "dummy-data" {})]
      (is (not (first validated)))
      (is (= "Could not find the validation for this data category." (validation-error validated)))))

  (testing "header validation failed"
    (let [validated (validate-content "development-data" (io/file "test-data/bad-header.csv"))]
      (is (not (first validated)))
      (is (= "The header row of the file is missing one or more required rows. We expect ward.name,borough,gss.code.ward,gss.code.borough" (validation-error validated)))))

  (testing "validation ok"
    (let [validated (validate-content "development-data" (io/file "test-data/Template_DevelopmentData_Bexley_WithData.csv"))]
      (is (first validated))
      (is (nil? (second validated))))))
