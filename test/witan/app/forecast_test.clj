(ns witan.app.forecast-test
  (:require [witan.app.forecast :refer :all]
            [witan.app.model :as model]
            [clojure.test :refer :all]
            [qbits.hayt :as hayt]))


(deftest test-property-values-handling
  (testing "numerical property"
    (with-redefs [model/get-model-by-model-id (fn [id]
                                                {:properties [{:name "number field" :type "number" :context "this is a number field"}]})]
      (testing "valid numerical property"
        (let [checked-property-values (check-property-values "fake-model-id" [{:name "number field" :value "123"}])
              _ (println "res" checked-property-values)]
          (is (= (:values checked-property-values) {"number field" (hayt/user-type {:name "number field":value "123" :type "number"})}))))))
  (testing "text property")
  (testing "number property")
  (testing "several properties"))
