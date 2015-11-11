(ns witan.app.model-execution-test
  (:require [witan.app.model-execution :refer :all]
            [clojure.test :refer :all]))

(def base-population-default-id #uuid "bf1c8571-4290-49c0-878e-0c2493ccf98e")
(def development-data-id #uuid "a2e9ef7c-079a-4846-a6b0-acffba2c600a")
(def high-trend-data-id #uuid "b66ea9ff-f161-4171-9872-cfbb32bd04c2")
(def low-trend-data-id #uuid "ceecdc02-cbaa-427c-b051-221a91c2fe19")

(def input-category-a {:category "Development data"
                       :description "This is a description of the development data input"})
(def input-category-b {:category "Base population data"
                       :description "These are the base population figures"})

(def input-category-c {:category "Low Trend Data"
                       :description "trend data description here"})

(def output-category {:category "Output Data"
                      :description "this is an expected output"})

(def dummy-forecast
  {:description "Description of my forecast"
   :model_property_values {}
   :owner_name nil
   :forecast_id #uuid "6591abcb-7bbf-472c-afcd-5539e7bd2e76"
   :version_id #uuid "544c2032-321b-4a47-a18c-b8da9849f93a"
   :name "My Forecast 1"
   :created #inst "2015-10-30T17:51:21.244-00:00"
   :inputs {"Development data" {:data_id development-data-id
                                :category input-category-a
                                :name "boo"
                                :publisher #uuid "03b1039b-bd0e-4f24-a503-44b5c3f81130"
                                :version 1
                                :file_name "boo.csv"
                                :s3_key #uuid "16f885ee-f793-431e-b615-a227119f4a60"
                                :created #inst "2015-10-30T17:51:21.235-00:00"}
            "High trend data" {:s3_key #uuid "33a7b684-79cb-4fb5-870d-adc15a87ae84"
                               :data_id high-trend-data-id
                               :category input-category-b
                               :created #inst "2015-11-06T12:59:01.552-00:00"
                               :file_name "base-population.csv"
                               :name "base population Ealing"
                               :publisher #uuid "f132d30c-adf9-4385-8f26-baa4525a4bf0"
                               :version 1}}
   :model_id #uuid "8dc43736-2e15-4c12-8f19-e58e833981e2"
   :in_progress true
   :outputs {}
   :version 3
   :owner #uuid "03b1039b-bd0e-4f24-a503-44b5c3f81130"})

(def dummy-model
  {:description "Description of my model"
   :properties []
   :version_id #uuid "49db8f19-5843-4fe0-b815-e7d3a9d85823"
   :input_data [input-category-a input-category-b]
   :name "Housing Linked Model"
   :output_data [output-category]
   :input_data_defaults {"Base population data" {:s3_key #uuid "4348bec5-12db-48bc-be28-2c4323f91197"
                                                 :data_id base-population-default-id
                                                 :category input-category-a
                                                 :created #inst "2015-11-06T12:59:01.517-00:00"
                                                 :file_name "Long+Pop.csv"
                                                 :name "London base population"
                                                 :publisher #uuid "f132d30c-adf9-4385-8f26-baa4525a4bf0"
                                                 :version 1}}
   :fixed_input_data {"Low Trend Data" {:category input-category-c
                                        :name "Low Trend Data GLA"
                                        :publisher #uuid "62d61b07-b658-430f-90b4-2e763e1df0ff"
                                        :version 1
                                        :data-id low-trend-data-id
                                        :file-name "low-trend.csv"
                                        :s3-key #uuid "6b6f285c-b7d6-4846-bca1-55a76ee671b9"
                                        :created "2015-11-11T13:17:20"} }
   :created #inst "2015-10-30T17:50:43.410-00:00"
   :model_id #uuid "8dc43736-2e15-4c12-8f19-e58e833981e2"
   :version 1
   :owner #uuid "b4c330be-e1fa-460d-857f-4ac20b8ddec2"})

(deftest model-execution-test
  (testing "gets the right inputs out form forecast and models"
    (let [given-inputs (get-inputs dummy-forecast dummy-model)]
      (is (== (:data_id (get "Base population data" given-inputs) base-population-default-id)))
      (is (== (:data_id (get "High trend data" given-inputs) high-trend-data-id)))
      (is (== (:data_id (get "Development data" given-inputs) development-data-id)))
      (is (== (:data_id (get "Low Trend Data" given-inputs) low-trend-data-id)))))
  (testing "missing data error"
    (let [incomplete-forecast (assoc dummy-forecast :inputs (dissoc (:inputs dummy-forecast "Development data")))]
      (comment (is (thrown-with-msg? Exception
                                     #"Incomplete input data for model: Housing Linked Model"
                                     (get-inputs incomplete-forecast dummy-model)))))))
