(ns witan.app.handler-test
  (:require [clojure.test :refer :all]
            [compojure.api.test-utils :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [witan.app.handler :refer :all]
            [witan.app.user :as user]
            [witan.app.forecast :as forecast]
            [clojure.data.json :as json]))

(def user-id (java.util.UUID/randomUUID))

(defn get-dummy-forecasts []
  '({:description "Description of my forecast",
     :name "My Forecast 1",
     :descendant_id nil,
     :created #inst "2015-10-06T12:44:17.176-00:00",
     :series_id #uuid "ca7928d8-ea7d-4bdb-ab16-4c6ae8912830",
     :in_progress false,
     :id #uuid "fd44474d-e0f8-4713-bacf-299e503e4f30",
     :version 0,
     :owner #uuid "cac4ba3a-07c8-4e79-9ae0-d97317bb0d45"}
    {:description "Description of my forecast",
     :name "My Forecast 2",
     :descendant_id nil,
     :created #inst "2015-10-06T12:44:17.210-00:00",
     :series_id #uuid "768f40f8-cf06-4da6-8b98-5227034f7dd5",
     :in_progress false,
     :id #uuid "102fef0c-aa17-41bc-9f4e-cc11d18d7ae5",
     :version 0,
     :owner #uuid "6961ed51-e1d6-4890-b102-ab862893e3ba"}
    {:description "Description of my forecast",
     :name "My Forecast 3",
     :descendant_id nil,
     :created #inst "2015-10-06T12:44:17.240-00:00",
     :series_id #uuid "197481d6-df2a-4175-a288-d596a9709322",
     :in_progress false,
     :id #uuid "7185c4e4-739e-4eb8-8e37-f3f4b618ac1d",
     :version 0,
     :owner #uuid "cac4ba3a-07c8-4e79-9ae0-d97317bb0d45"}))

(defn auth-header [token] {"Authorization" (str "Token " token)})

(defn logged-in-user-token []
  (with-redefs [user/user-valid? (fn [username password] {:id user-id})]
    (let [[_ body _] (post* app "/api/login" {:body (json {"username" "test@test.com" "password" "secret"})})]
      (:token body))))

(deftest test-app
  (testing "/api/"
    (testing "endpoint unauthorized"
      (let [[status body _] (get* app "/api/" {})]
        (is (= status 401))
        (is (= body {:error "Unauthorized"})))))

  (testing "/api/login"
    (testing "login success"
      (with-redefs [user/user-valid? (fn [username password] {:id user-id})]
        (let [[status body _] (post* app "/api/login" {:body (json {"username" "support@mastodonc.com" "password" "secret"})})]
          (is (= status 200))
          (is (contains? body :token))
          (is (contains? body :id)))))

    (testing "login failure"
      (with-redefs [user/user-valid? (fn [username password] false)]
        (let [[status body _] (post* app "/api/login" {:body (json {"username" "blah@blah.blah" "password" "foobar"})})]
          (is (= status 200))
          (is (not (contains? body :token)))
          (is (not (contains? body :id))))))

    (testing "logged in user"
      (with-redefs [user/user-valid? (fn [username password] {:id user-id})]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/" {} (auth-header token))]
          (is (= status 200))
          (is (contains? body :message))))))

  (testing "/api/user"
    (testing "sign up"
      (with-redefs [user/add-user! (fn [user] ())]
        (let [[status body _] (post* app "/api/user" {:body (json {"username" "test@test.com" "password" "sekrit" "name" "Arthur Dent"})})]
          (is (= status 201))
          (is (contains? body :token))
          (is (contains? body :id))))))

  (testing "/api/me"
    (testing "401 when not logged in"
      (with-redefs [user/retrieve-user (fn [id] {:id id :name "Joe" :username "joe@test.com"})]
        (let [[status body _] (get* app "/api/me" {})]
          (is (= status 401)))))

    (testing "retrieve user"
      (with-redefs [user/retrieve-user (fn [id] {:id id :name "Joe" :username "joe@test.com"})]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/me" {} (auth-header token))]
          (is (= status 200))))))

  (testing "/api/forecasts"
    (testing "get forecasts"
      (with-redefs [forecast/get-forecasts get-dummy-forecasts]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/forecasts" {} (auth-header token))]
          (is (= status 200))
          (is (seq? body))))))

  (testing "not-found route"
    (let [[status body _] (get* app "/invalid")]
      (is (= status 404)))))
