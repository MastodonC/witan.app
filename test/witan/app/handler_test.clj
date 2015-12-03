(ns witan.app.handler-test
  (:require [clojure.test :refer :all]
            [compojure.api.test-utils :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [witan.app.handler :refer :all]
            [witan.app.user :as u]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.data :as data]
            [witan.app.schema :as ws]
            [witan.app.s3 :as s3]
            [clojure.data.json :as json]
            [schema.core :as s]))

(def user-id (java.util.UUID/randomUUID))

(defn get-dummy-forecast-headers [& _]
  '({:description "Description of my forecast",
     :name "My Forecast 1",
     :created #inst "2015-10-06T12:44:17.176-00:00",
     :current_version_id #uuid "78b1bf97-0ebe-42ef-8031-384e504cf795",
     :in_progress false,
     :forecast_id #uuid "fd44474d-e0f8-4713-bacf-299e503e4f30",
     :version 2,
     :owner #uuid "cac4ba3a-07c8-4e79-9ae0-d97317bb0d45",
     :owner_name "User 1"
     :public false}
    {:description "Description of my forecast",
     :name "My Forecast 2",
     :created #inst "2015-10-06T12:44:17.210-00:00",
     :forecast_id #uuid "768f40f8-cf06-4da6-8b98-5227034f7dd5",
     :in_progress false,
     :current_version_id #uuid "102fef0c-aa17-41bc-9f4e-cc11d18d7ae5",
     :version 0,
     :owner #uuid "6961ed51-e1d6-4890-b102-ab862893e3ba",
     :owner_name "User 2"
     :public false}
    {:description "Description of my forecast",
     :name "My Forecast 3",
     :created #inst "2015-10-06T12:44:17.240-00:00",
     :current_version_id #uuid "197481d6-df2a-4175-a288-d596a9709322",
     :in_progress false,
     :forecast_id #uuid "7185c4e4-739e-4eb8-8e37-f3f4b618ac1d",
     :version 0,
     :owner #uuid "cac4ba3a-07c8-4e79-9ae0-d97317bb0d45",
     :owner_name "User 1"
     :public false}))

(defn get-dummy-forecasts [& _]
  '({:forecast_id #uuid "fd44474d-e0f8-4713-bacf-299e503e4f30",
     :version 2,
     :created #inst "2015-10-14T08:41:21.477-00:00",
     :description "Description of my forecast",
     :in_progress false,
     :latest true
     :name "My Forecast 1",
     :owner #uuid "d8fc0f3c-0535-4959-bf9e-505af9a59ad9",
     :owner_name "User 3",
     :public false
     :version_id #uuid "78b1bf97-0ebe-42ef-8031-384e504cf795"
     :model_id #uuid "dbd5d07e-ec05-4409-83da-71971897cfa0"
     :model_property_values {}
     :inputs {"Base population data" {:data_id #uuid "40ff789b-68dd-420d-81e7-2b19b69fd399",
                                      :category "Base population data",
                                      :name "base population Camden",
                                      :publisher #uuid "bd163a4b-fecc-4f8d-a642-c9ee951d6f77",
                                      :version 1,
                                      :public false
                                      :file_name "base-population.csv",
                                      :s3_key #uuid "56f6ee27-8357-4108-a450-edfa4ad3c7cd",
                                      :created #inst "2015-10-28T18:27:33.967-00:00" } }}
    {:forecast_id #uuid "fd44474d-e0f8-4713-bacf-299e503e4f30",
     :version 1,
     :created #inst "2015-10-14T08:41:21.253-00:00",
     :description "Description of my forecast",
     :in_progress false,
     :latest false,
     :name "My Forecast 1",
     :owner #uuid "d8fc0f3c-0535-4959-bf9e-505af9a59ad9",
     :owner_name "User 3",
     :public false
     :version_id #uuid "f960e442-2c85-489e-9807-4eeecd6fd55a"
     :model_id #uuid "dbd5d07e-ec05-4409-83da-71971897cfa0"}
    {:description "Description of my forecast",
     :name "My Forecast 1",
     :created #inst "2015-10-06T12:44:17.176-00:00",
     :version_id #uuid "ca7928d8-ea7d-4bdb-ab16-4c6ae8912830",
     :in_progress false,
     :latest false,
     :forecast_id #uuid "fd44474d-e0f8-4713-bacf-299e503e4f30",
     :version 0,
     :owner #uuid "cac4ba3a-07c8-4e79-9ae0-d97317bb0d45",
     :owner_name "User 1"
     :public false
     :model_id #uuid "dbd5d07e-ec05-4409-83da-71971897cfa0"}))

(defn get-dummy-models [& _]
  '({:name "My Model 2",
     :created #inst "2015-10-09T13:52:43.951-00:00",
     :description "Description of my model",
     :model_id #uuid "d92e4e09-ede5-48a1-95f4-c3b15b0ba399",
     :owner #uuid "c9d5bfa6-9517-4d28-8c86-7232c8d92352",
     :properties [],
     :version 0,
     :version_id #uuid "5012d65c-20fe-4fdf-b3cc-2a1e5760f52a"
     :input_data [{:category "Base population data" :description "A description of the input"}]}
    {:name "My Model 1",
     :created #inst "2015-10-09T13:52:43.865-00:00",
     :description "Description of my model",
     :model_id #uuid "c1197cb3-54c3-4b59-ae20-384c64b95095",
     :owner #uuid "f6d452f1-3978-4e8a-ab38-58fab1949c7c",
     :properties [],
     :version 0,
     :version_id #uuid "653c149a-86d8-4a3f-a7a8-d898c070177e"}))

(defn get-dummy-model []
  {:description "Description of my model", :properties [{:name "Some field", :type "text", :context "Placeholder value 123", :enum_values []}], :version_id #uuid "fa200d2d-816d-4502-b94a-9ba020f2f1f4", :input_data ["Base population data"], :name "My Model 2", :output_data ["All the population data"], :input_data_defaults {}, :created #inst "2015-10-21T10:51:22.093-00:00", :model_id #uuid "dbd5d07e-ec05-4409-83da-71971897cfa0", :version 1, :owner #uuid "98f9adcb-bc80-407c-b9c8-736506f6e410"})

(defn get-dummy-data []
  '({:category "Base population data", :created #inst "2015-10-28T18:27:33.968-00:00", :data_id #uuid "40ff789b-68dd-420d-81e7-2b19b69fd399", :file_name "base-population.csv", :name "base population Camden", :publisher #uuid "bd163a4b-fecc-4f8d-a642-c9ee951d6f77", :s3_key #uuid "56f6ee27-8357-4108-a450-edfa4ad3c7cd", :version 1 :public false}))

(defn auth-header [token] {"Authorization" (str "Token " token)})

(defn logged-in-user-token []
  (with-redefs [u/user-valid? (fn [username password] {:id user-id})]
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
      (with-redefs [u/user-valid? (fn [username password] {:id user-id})]
        (let [[status body _] (post* app "/api/login" {:body (json {"username" "support@mastodonc.com" "password" "secret"})})]
          (is (= status 200))
          (is (contains? body :token))
          (is (contains? body :id)))))

    (testing "login failure"
      (with-redefs [u/user-valid? (fn [username password] false)]
        (let [[status body _] (post* app "/api/login" {:body (json {"username" "blah@blah.blah" "password" "foobar"})})]
          (is (= status 200))
          (is (not (contains? body :token)))
          (is (not (contains? body :id))))))

    (testing "logged in user"
      (with-redefs [u/user-valid? (fn [username password] {:id user-id})]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/" {} (auth-header token))]
          (is (= status 200))
          (is (contains? body :message))))))

  (testing "/api/user"
    (testing "sign up"
      (with-redefs [u/add-user! (fn [user] ())]
        (let [[status body _] (post* app "/api/user" {:body (json {"username" "test@test.com" "password" "sekrit" "name" "Arthur Dent"})})]
          (is (= status 201))
          (is (contains? body :token))
          (is (contains? body :id))))))

  (testing "/api/me"
    (testing "401 when not logged in"
      (with-redefs [u/retrieve-user (fn [id] {:id id :name "Joe" :username "joe@test.com"})]
        (let [[status body _] (get* app "/api/me" {})]
          (is (= status 401)))))

    (testing "401 with bogus token"
      (with-redefs [u/retrieve-user (fn [id] {:id id :name "Joe" :username "joe@test.com"})]
        (let [[status body _] (get* app "/api/me" {} (auth-header "BOGUSTOKEN"))]
          (is (= status 401)))))

    (testing "retrieve user"
      (with-redefs [u/retrieve-user (fn [id] {:id id :name "Joe" :username "joe@test.com"})]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/me" {} (auth-header token))]
          (is (= status 200))))))

  (testing "/api/forecasts"
    (testing "get forecasts"
      (with-redefs [forecast/get-forecasts get-dummy-forecast-headers]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/forecasts" {} (auth-header token))]
          (is (= status 200))
          (is (seq? body)))))

    (testing "get forecast versions"
      (with-redefs [witan.app.config/exec get-dummy-forecasts]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/forecasts/fd44474d-e0f8-4713-bacf-299e503e4f30" {} (auth-header token))]
          (is (= status 200))
          (is (seq? body)))))

    (testing "get forecast specific version"
      (with-redefs [witan.app.config/exec (fn [_] (vector (second (get-dummy-forecasts))))]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/forecasts/fd44474d-e0f8-4713-bacf-299e503e4f30/1" {} (auth-header token))]
          (is (= status 200))
          (is (not (seq? body)))
          (is (= (:version body) 1))
          (is (= (:version-id body) "f960e442-2c85-489e-9807-4eeecd6fd55a")))))

    (testing "get forecast latest version"
      (with-redefs [witan.app.config/exec (fn [_] (vector (first (get-dummy-forecasts))))]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/forecasts/fd44474d-e0f8-4713-bacf-299e503e4f30/latest" {} (auth-header token))]
          (is (= status 200))
          (is (not (seq? body)))
          (is (= (:version body) 2))
          (is (= (:version-id body) "78b1bf97-0ebe-42ef-8031-384e504cf795")))))

    ;; TODO fix this, for some reason :identity is not being assoc'd into the
    ;; request -- literally, no idea why.
    ;;(testing "post forecasts"
    ;;  (with-redefs []
    ;;    (let [token (logged-in-user-token)
    ;;          [status body _] (raw-post* app "/api/forecasts" {:body (json {"name" "My New Forecast 1"})} nil (auth-header token))]
    ;;      (is (= status 201))))))
    )

  (testing "/api/forecasts/:forecast-id/versions"
    (with-redefs [data/add-data! (fn [_] )
                  forecast/update-forecast! (fn [forecast-id owner inputs] (first (get-dummy-forecasts)))
                  s3/exists? (fn [_] true)]
      (let [token (logged-in-user-token)
            [status body _] (post* app "/api/forecasts/b7b35c0b-bbf0-4a52-ab40-6264ed0f364d/versions" {:body (json {"Base population data" {"category" "development-data" "publisher" "5db2c4ab-77f9-4e2f-839d-776faf4c5453" "name" "base population" "created" "2015-11-30T22:25:31" "file-name" "file.csv" "public?" "null" "s3-key" "653ceaad-cf3b-467c-966c-04b57f443708" "version" "1"}})})])))

  (testing "/api/data/:category"
    (with-redefs [data/get-data-by-category (fn [_ _] (get-dummy-data))]
      (let [token (logged-in-user-token)
            [status body _] (get* app "/api/data/Base%20population%20data" {} (auth-header token))]
        (is (= status 200))
        (is (seq? body)))))

  (testing "/api/models"
    (testing "get models"
      (with-redefs [model/get-models get-dummy-models]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/models" {} (auth-header token))]
          (is (= status 200))
          (is (seq? body))))))

  (testing "not-found route"
    (let [[status body _] (get* app "/invalid")]
      (is (= status 404)))))
