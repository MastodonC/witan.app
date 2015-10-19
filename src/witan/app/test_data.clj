(ns witan.app.test-data
  (:require [witan.app.user :as user]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.data :as data]
            [witan.app.config :as c]))

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
                              :input-data [{:name "Base population data"}]})
        m2 (model/add-model! {:name "My Model 2"
                              :description "Description of my model"
                              :owner (:id user2)
                              :properties [{:name "Some field" :type "text" :context "Placeholder value 123"}]
                              :input-data [{:name "Base population data"}]})
        m3 (model/add-model! {:name "My Model 3"
                              :description "Model with enum"
                              :owner (:id user2)
                              :properties [{:name "Boroughs" :type "dropdown" :context "Choose a borough" :enum_values ["Camden" "Richmond Upon Thames" "Hackney" "Barnet"]}]
                              :input-data [{:name "SHLAA"}
                                           {:name "Base population data"}]
                              :output-data [{:name "housing-linked population"}]})
        ;; add data
        d1 (data/add-data! {:name "Base population"
                            :publisher (:id user1)
                            :s3-url "https://s3.eu-central-1.amazonaws.com/witan-test-data/Long+Pop.csv"
                            :model-id (:model_id m1)})

        ;; update model to have this as default data


        ;; versions of these models
        ;;m1_2 (model/update-model! {:model-id (:model_id m1) :owner (:id user1)})

        ;; add a few forecasts
        f1 (forecast/add-forecast! {:name "My Forecast 1" :description "Description of my forecast" :owner (:id user1) :model-id (:model_id m1)})
        f2 (forecast/add-forecast! {:name "My Forecast 2" :description "Description of my forecast" :owner (:id user2) :model-id (:model_id m1)})
        f3 (forecast/add-forecast! {:name "My Forecast 3" :description "Description of my forecast" :owner (:id user1) :model-id (:model_id m2) :model-properties [{:name "Some field" :value "ole"}]})

        ;; versions of these forecasts
        f1_1 (forecast/update-forecast! {:forecast-id (:forecast_id f1) :owner (:id user1)})
        f1_2 (forecast/update-forecast! {:forecast-id (:forecast_id f1) :owner (:id user1)})
        f3_1 (forecast/update-forecast! {:forecast-id (:forecast_id f3) :owner (:id user1)})]))
