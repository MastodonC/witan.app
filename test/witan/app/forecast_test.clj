(ns witan.app.forecast-test
  (:require [witan.app.forecast :refer :all]
            [witan.app.model :as model]
            [clojure.test :refer :all]
            [qbits.hayt :as hayt]))


(deftest test-property-values-handling
  (testing "numerical property"
    (with-redefs [model/get-model-by-model-id (fn [_]
                                                {:properties [{:name "number field" :type "number" :context "this is a number field"}]})]
      (testing "valid numerical property"
        (let [checked-property-values (check-property-values "fake-model-id" [{:name "number field" :value "123"}])]
          (is (= (:values checked-property-values) {"number field" (hayt/user-type {:name "number field":value "123" :type "number"})}))
          (is (empty? (:errors checked-property-values)))))
      (testing "invalid numerical property"
        (let [checked-property-values (check-property-values "fake-model-id" [{:name "number field" :value "boo123"}])]
          (is (= (:errors checked-property-values) ["Wrong type for number field"]))
          (is (empty? (:values checked-property-values)))))))
  (testing "text property"
    (with-redefs [model/get-model-by-model-id (fn [_]
                                                {:properties [{:name "text field" :type "text" :context "this is a text field"}]})]
      (let [checked-property-values (check-property-values "fake-model-id" [{:name "text field" :value "wonderful world"}])]

        (is (= (:values checked-property-values) {"text field" (hayt/user-type {:name "text field" :value "wonderful world" :type "text"})})))))
  (testing "number property")
  (testing "several properties")
  (testing "no properties"))
