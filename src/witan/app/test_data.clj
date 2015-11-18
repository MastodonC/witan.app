(ns witan.app.test-data
  (:require [witan.errors :as e]
            [witan.app.config :as c]
            [witan.app.data :as data]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.user :as u]))

(def population-fixed-input  {:category "population"
                              :description "DCLG public population data"})
(def institutional-fixed-input {:category "institutional"
                                :description "Institutional population, also known as Communal establishment pop (CEP) i.e. people living in student halls, retirement homes etc."})
(def private-housing-fixed-input {:category "private-housing"
                                  :description "Household population"})
(def household-fixed-input {:category "households-data"
                            :description "Number of households"})
(def low-trend-fixed-input {:category "low-trend-data"
                            :description "Low migration scenario trend-based population projections"})
(def high-trend-fixed-input {:category "high-trend-data"
                             :description "High migration scenario trend-based population projections"})
(def dwellings-data-fixed-input {:category "dwellings-data"
                                 :description "Dwellings from most recent census"})

(def development-category {:category "development-data"
                           :description "New dwellings projections based on planning permissions entered and checked by the Boroughs"})
(def output-category {:category "housing-linked-population"
                      :description "Housing linked population figures"})

(defn load-test-data!
  "Add a bunch of test data into Cassandra"
  []
  (let [;; add users
        user1 (u/add-user! {:name "Mastodon 1" :username "support@mastodonc.com" :password "secret"})
        user2 (u/add-user! {:name "Mastodon 2" :username "support2@mastodonc.com" :password "secret"})

        ;; fixed data sources
        ;; Note: data is uploaded in S3 with given keys in both witan-test-data and witan-staging-data buckets
        population-data (data/add-data! {:category  (:category population-fixed-input)
                                         :name      "Census population"
                                         :publisher (:id user1)
                                         :file-name "Long+Pop.csv"
                                         :s3-key #uuid "ecef4186-0d6a-452b-9713-cdce3437cd59"})
        institutional-data (data/add-data! {:category  (:category institutional-fixed-input)
                                            :name      "Institutional population"
                                            :publisher (:id user1)
                                            :file-name "Long+Inst+Pop.csv"
                                            :s3-key #uuid "59400efa-b002-4eae-849b-34751b458f74"})
        private-housing-data (data/add-data! {:category (:category private-housing-fixed-input)
                                              :name "Private Housing"
                                              :publisher (:id user1)
                                              :file-name "Long+PHP.csv"
                                              :s3-key #uuid "f70f99c3-10d1-4271-a30b-dc1290117943"})
        households-data (data/add-data! {:category (:category household-fixed-input)
                                         :name "Households"
                                         :publisher (:id user1)
                                         :file-name "Long+HH.csv"
                                         :s3-key #uuid "fad572a5-6d90-4bbc-8874-62a2886b3ce6"})
        low-trend-data (data/add-data! {:category (:category low-trend-fixed-input)
                                        :name "Low trend data"
                                        :publisher (:id user1)
                                        :file-name "Low+-+SYA.csv"
                                        :s3-key #uuid "72aea88c-24cf-4213-a6f3-a3e2b36b8604"})
        high-trend-data (data/add-data! {:category (:category high-trend-fixed-input)
                                         :name "High trend data"
                                         :publisher (:id user1)
                                         :file-name "High+-+SYA.csv"
                                         :s3-key #uuid "765a4acc-0e54-4f17-9d4e-365c7bdd3fb2"})
        dwellings-data (data/add-data! {:category (:category dwellings-data-fixed-input)
                                        :name "Dwellings Data"
                                        :publisher (:id user1)
                                        :file-name "dwellings.csv"
                                        :s3-key #uuid "cb28732e-3d5b-43eb-945f-d9471c983161"})
        development-data (data/add-data! {:category (:category development-category)
                                          :name "New Dwellings Data - SHLAA GLA"
                                          :publisher (:id user1)
                                          :file-name "development.csv"
                                          :s3-key #uuid "d9bd8135-d330-43d5-a4f3-fc2a096b2774"})
        ;; model
        dclg-housing-linked-model (model/add-model! {:name "DCLG-based Housing Linked Model"
                                                     :description "Demography model which was developed to generate population projections that are consistent with an input housing trajectory. The title includes DCLG because it draws on data from the Department of Communities and Local Government (DCLG)."
                                                     :owner (:id user1)
                                                     :properties [{:name "borough"
                                                                   :type "dropdown"
                                                                   :context "Please choose a London borough"
                                                                   :enum_values ["Barking and Dagenham"
                                                                                 "Barnet"
                                                                                 "Bexley"
                                                                                 "Brent"
                                                                                 "Bromley"
                                                                                 "Camden"
                                                                                 "Croydon"
                                                                                 "Ealing"
                                                                                 "Enfield"
                                                                                 "Greenwich"
                                                                                 "Hackney"
                                                                                 "Hammersmith and Fulham"
                                                                                 "Haringey"
                                                                                 "Harrow"
                                                                                 "Havering"
                                                                                 "Hillingdon"
                                                                                 "Hounslow"
                                                                                 "Islington"
                                                                                 "Kensington and Chelsea"
                                                                                 "Kingston upon Thames"
                                                                                 "Lambeth"
                                                                                 "Lewisham"
                                                                                 "Merton"
                                                                                 "Newham"
                                                                                 "Redbridge"
                                                                                 "Richmond upon Thames"
                                                                                 "Southwark"
                                                                                 "Sutton"
                                                                                 "Tower Hamlets"
                                                                                 "Waltham Forest"
                                                                                 "Wandsworth"
                                                                                 "Westminster"]}]
                                                     :input-data [development-category]
                                                     :output-data [output-category]
                                                     :fixed-input-data [{:category (:category population-fixed-input)
                                                                         :data (data/->Data population-data)}
                                                                        {:category (:category institutional-fixed-input)
                                                                         :data (data/->Data institutional-data)}
                                                                        {:category (:category private-housing-fixed-input)
                                                                         :data (data/->Data private-housing-data)}
                                                                        {:category (:category household-fixed-input)
                                                                         :data (data/->Data households-data)}
                                                                        {:category (:category low-trend-fixed-input)
                                                                         :data (data/->Data low-trend-data)}
                                                                        {:category (:category high-trend-fixed-input)
                                                                         :data (data/->Data high-trend-data)}
                                                                        {:category (:category dwellings-data-fixed-input)
                                                                         :data (data/->Data dwellings-data)}]})
        broken-model (model/add-model! {:name "Broken Model"
                                        :description "This model will always produce an error."
                                        :owner (:id user1)
                                        :properties [{:name "error"
                                                      :type "dropdown"
                                                      :context "Please choose which error to produce."
                                                      :enum_values (->> e/error-codes vals (mapv name))}]
                                        :input-data [development-category]
                                        :output-data [output-category]
                                        :fixed-input-data []})
        f1 (forecast/add-forecast! {:name        "Housing Linked Model Islington"
                                    :description "DCLG Housing Linked Model for the borough of Islington"
                                    :owner       (:id user1)
                                    :model-id    (:model_id dclg-housing-linked-model)
                                    :model-properties [{:name "borough" :value "Islington"}]})
        f2 (forecast/add-forecast! {:name        "Housing Linked Model Camden"
                                    :description "DCLG Housing Linked Model for the borough of Camden"
                                    :owner       (:id user1)
                                    :model-id    (:model_id dclg-housing-linked-model)
                                    :model-properties [{:name "borough" :value "Camden"}]})
        f1_1 (forecast/update-forecast! {:forecast-id (:forecast_id f1)
                                         :owner (:id user1)
                                         :inputs {(:category development-category) development-data}})
        ]))
