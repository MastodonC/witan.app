(ns witan.app.test-data
  (:require [witan.app.user :as user]
            [witan.app.forecast :as forecast]
            [witan.app.config :as c]))

(defn load-test-data
  "Add a bunch of test data into Cassandra"
  []

  ;; add users
  (user/add-user! {:name "Mastodon 1" :username "support@mastodonc.com" :password "secret"})
  (user/add-user! {:name "Mastodon 2" :username "support2@mastodonc.com" :password "secret"})
  (let [id1 (:id (first (c/exec (user/find-user-by-username "support@mastodonc.com"))))
        id2 (:id (first (c/exec (user/find-user-by-username "support2@mastodonc.com"))))]

    ;; add a few forecasts
    (forecast/add-forecast! {:name "My Forecast 1" :description "Description of my forecast" :owner id1})
    (forecast/add-forecast! {:name "My Forecast 2" :description "Description of my forecast" :owner id2})
    (forecast/add-forecast! {:name "My Forecast 3" :description "Description of my forecast" :owner id1})))
