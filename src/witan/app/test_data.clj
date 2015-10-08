(ns witan.app.test-data
  (:require [witan.app.user :as user]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.config :as c]))

(defn load-test-data!
  "Add a bunch of test data into Cassandra"
  []
  (let [;; add users
        user1 (user/add-user! {:name "Mastodon 1" :username "support@mastodonc.com" :password "secret"})
        user2 (user/add-user! {:name "Mastodon 2" :username "support2@mastodonc.com" :password "secret"})

        ;; add models
        m1 (model/add-model! {:name "My Model 1" :description "Description of my model" :owner (:id user1)})
        m2 (model/add-model! {:name "My Model 2" :description "Description of my model" :owner (:id user2)
                              :properties [{:name "Some field" :type "text" :context "Placeholder value 123"}]})

        ;; versions of these models
        ;;m1_2 (model/update-model! {:model-id (:model_id m1) :owner (:id user1)})


        ;; add a few forecasts
        f1 (forecast/add-forecast! {:name "My Forecast 1" :description "Description of my forecast" :owner (:id user1)})
        f2 (forecast/add-forecast! {:name "My Forecast 2" :description "Description of my forecast" :owner (:id user2)})
        f3 (forecast/add-forecast! {:name "My Forecast 3" :description "Description of my forecast" :owner (:id user1)})

        ;; versions of these forecasts
        f1_1 (forecast/update-forecast! {:id (:forecast_id f1) :owner (:id user1)})
        f1_2 (forecast/update-forecast! {:id (:forecast_id f1) :owner (:id user1)})
        f3_1 (forecast/update-forecast! {:id (:forecast_id f3) :owner (:id user1)})]))
