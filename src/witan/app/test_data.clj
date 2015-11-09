(ns witan.app.test-data
  (:require [witan.app.config :as c]
            [witan.app.data :as data]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.user :as user]))

(defn load-test-data!
  "Add a bunch of test data into Cassandra"
  []
  (let [;; add users
        user1 (user/add-user! {:name "Mastodon 1" :username "support@mastodonc.com" :password "secret"})
        user2 (user/add-user! {:name "Mastodon 2" :username "support2@mastodonc.com" :password "secret"})
        ;; add models
        m1 (model/add-model! {:name "My Model 1"
                              :description "Description of my model"
                              :owner (:id user1)
                              :input-data [{:category "Base population data"}]
                              :output-data [{:category "wishful thinking"}]})
        m2 (model/add-model! {:name "My Model 2"
                              :description "Description of my model"
                              :owner (:id user2)
                              :properties [{:name "Some field" :type "text" :context "Placeholder value 123"}]
                              :input-data [{:category "Base population data"}]
                              :output-data [{:category "All the population data"}]})
        m3 (model/add-model! {:name "My Model 3"
                     
                              :description "Model with enum"
                              :owner (:id user2)
                              :properties [{:name "Boroughs" :type "dropdown" :context "Choose a borough" :enum_values ["Camden" "Richmond Upon Thames" "Hackney" "Barnet"]}]
                              :input-data [{:category "long population"}
                                           {:category "overlay housing"}
                                           {:category "trend data"}]
                              :output-data [{:category "housing-linked population"}]})
        ;; add data
        d1 (data/add-data! {:category "long population"
                            :name "London base population"
                            :publisher (:id user1)
                            :file-name "Long+Pop.csv"
                            :s3-key #uuid "4348bec5-12db-48bc-be28-2c4323f91197" })
        d2 (data/add-data! {:category "Base population data"
                            :name "base population Camden"
                            :publisher (:id user2)
                            :file-name "base-population.csv"
                            :s3-key #uuid "56f6ee27-8357-4108-a450-edfa4ad3c7cd"})
        d3 (data/add-data! {:category "Base population data"
                            :name "base population Ealing"
                            :publisher (:id user1)
                            :file-name "base-population.csv"
                            :s3-key #uuid "33a7b684-79cb-4fb5-870d-adc15a87ae84"})

        ;; update model to have this as default data
        _ (model/add-default-data-to-model! (:model_id m3)
                                            "long population"
                                            d1)

        ;; versions of these models
        ;;m1_2 (model/update-model! {:model-id (:model_id m1) :owner (:id user1)})

        ;; add a few forecasts
        f1 (forecast/add-forecast! {:name "My Forecast 1" :description "Description of my forecast" :owner (:id user1) :model-id (:model_id m1)})
        f2 (forecast/add-forecast! {:name "My Forecast 2" :description "Description of my forecast" :owner (:id user2) :model-id (:model_id m1)})
        f3 (forecast/add-forecast! {:name "My Forecast 3" :description "Description of my forecast" :owner (:id user1) :model-id (:model_id m2) :model-properties [{:name "Some field" :value "ole"}]})

        ;; versions of these forecasts
        f1_1 (forecast/update-forecast! {:forecast-id (:forecast_id f1) :owner (:id user1) :inputs {"Base population data" d2}})
        f1_2 (forecast/update-forecast! {:forecast-id (:forecast_id f1) :owner (:id user1)})
        f3_1 (forecast/update-forecast! {:forecast-id (:forecast_id f3)
                                         :owner (:id user1)
                                         :inputs {"Base population data" d3}})

        ;; conclude f3
        f3_1_done (forecast/conclude-forecast! {:forecast-id (:forecast_id f3_1)
                                                :version (:version f3_1)
                                                :outputs nil}) ;; TODO nil outputs for now but needs to be something legit
        ]))
