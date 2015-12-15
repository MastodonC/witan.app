(ns witan.app.test-data
  (:require [witan.app.config :as c]
            [witan.app.data :as data]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.user :as u]
            [clojure.tools.logging :as log]))

(def population-fixed-input  {:category "population"
                              :description "Department of Communities and Local Government (DCLG) public population projections"})
(def institutional-fixed-input {:category "institutional"
                                :description "DCLG institutional population projections, also known as communal establishment pop (CEP) i.e. people living in student halls, retirement homes etc."})
(def private-housing-fixed-input {:category "private-housing"
                                  :description "DCLG household population projections i.e. people living in private rather than communal housing"})
(def household-fixed-input {:category "households-data"
                            :description "DCLG projections for numbers of households"})
(def low-trend-fixed-input {:category "low-trend-data"
                            :description "GLA-generated low migration scenario trend-based population projections"})
(def high-trend-fixed-input {:category "high-trend-data"
                             :description "GLA-generated high migration scenario trend-based population projections"})
(def dwellings-data-fixed-input {:category "dwellings-data"
                                 :description "Dwellings from most recent census"})

(def development-category {:category "development-data"
                           :description "Net new dwellings from London Development Database and projections of housing (SHLAA or BPO). [Download a template here.](/data/public/Template_DevelopmentData_{{ model-properties.borough|nowhitespace }}.csv)"})
(def output-category {:category "housing-linked-population"
                      :description "Housing-linked population projections"})

(defn load-test-data!
  "Loads test data into Cassandra - do not rely on this data, it's often broken"
  []
  (let [;; add users
        _ (log/info "Adding users...")
        user1 (u/add-user! {:name "Mastodon 1" :username "support+witan@mastodonc.com" :password "secret"})
        user2 (u/add-user! {:name "Mastodon 2" :username "support+witan22@mastodonc.com" :password "secret"})

        ;; fixed data sources
        ;; Note: data is uploaded in S3 with given keys in both witan-test-data and witan-staging-data buckets
        _ (log/info "Adding fixed data sources...")
        population-data (data/add-data! {:category  (:category population-fixed-input)
                                         :name      "DCLG population"
                                         :publisher (:id user1)
                                         :file-name "Long+Pop.csv"
                                         :s3-key #uuid "ecef4186-0d6a-452b-9713-cdce3437cd59"})
        institutional-data (data/add-data! {:category  (:category institutional-fixed-input)
                                            :name      "DCLG institutional population"
                                            :publisher (:id user1)
                                            :file-name "Long+Inst+Pop.csv"
                                            :s3-key #uuid "59400efa-b002-4eae-849b-34751b458f74"})
        private-housing-data (data/add-data! {:category (:category private-housing-fixed-input)
                                              :name "DCLG household population"
                                              :publisher (:id user1)
                                              :file-name "Long+PHP.csv"
                                              :s3-key #uuid "f70f99c3-10d1-4271-a30b-dc1290117943"})
        households-data (data/add-data! {:category (:category household-fixed-input)
                                         :name "DCLG households"
                                         :publisher (:id user1)
                                         :file-name "Long+HH.csv"
                                         :s3-key #uuid "fad572a5-6d90-4bbc-8874-62a2886b3ce6"})
        low-trend-data (data/add-data! {:category (:category low-trend-fixed-input)
                                        :name "Low trend"
                                        :publisher (:id user1)
                                        :file-name "Low+-+SYA.csv"
                                        :s3-key #uuid "72aea88c-24cf-4213-a6f3-a3e2b36b8604"})
        high-trend-data (data/add-data! {:category (:category high-trend-fixed-input)
                                         :name "High trend"
                                         :publisher (:id user1)
                                         :file-name "High+-+SYA.csv"
                                         :s3-key #uuid "765a4acc-0e54-4f17-9d4e-365c7bdd3fb2"})
        dwellings-data (data/add-data! {:category (:category dwellings-data-fixed-input)
                                        :name "Dwellings"
                                        :publisher (:id user1)
                                        :file-name "dwellings.csv"
                                        :s3-key #uuid "cb28732e-3d5b-43eb-945f-d9471c983161"})
        development-data (data/add-data! {:category (:category development-category)
                                          :name "Net new dwellings"
                                          :publisher (:id user1)
                                          :file-name "development.csv"
                                          :public? true
                                          :s3-key #uuid "d9bd8135-d330-43d5-a4f3-fc2a096b2774"})

        ;;;;

        high-fert-high-births-data (data/add-data! {:category "high-fert-high-births-data"
                                                    :name "high-fert-high-births-data"
                                                    :publisher (:id user1)
                                                    :file-name "high-fert-high-births-data.csv"
                                                    :public? true
                                                    :s3-key #uuid "3b8ffba5-cde3-4723-8052-63268b532dbe"})
        high-fert-high-deaths-data (data/add-data! {:category "high-fert-high-deaths-data"
                                                    :name "high-fert-high-deaths-data"
                                                    :publisher (:id user1)
                                                    :file-name "high-fert-high-deaths-data.csv"
                                                    :public? true
                                                    :s3-key #uuid "c16d3bbb-8968-49d2-ba14-7f04d0ea020a"})
        high-fert-high-sya-data (data/add-data! {:category "high-fert-high-sya-data"
                                                 :name "high-fert-high-sya-data"
                                                 :publisher (:id user1)
                                                 :file-name "high-fert-high-sya-data.csv"
                                                 :public? true
                                                 :s3-key #uuid "2821ea88-7710-46f4-91ad-f87329bb70e2"})
        high-fert-low-births-data (data/add-data! {:category "high-fert-low-births-data"
                                                   :name "high-fert-low-births-data"
                                                   :publisher (:id user1)
                                                   :file-name "high-fert-low-births-data.csv"
                                                   :public? true
                                                   :s3-key #uuid "10556772-6467-4e7a-8024-eea9a75bf39e"})
        high-fert-low-deaths-data (data/add-data! {:category "high-fert-low-deaths-data"
                                                   :name "high-fert-low-deaths-data"
                                                   :publisher (:id user1)
                                                   :file-name "high-fert-low-deaths-data.csv"
                                                   :public? true
                                                   :s3-key #uuid "c9bf8bc4-c806-426a-ba3f-b19544c94a00"})
        high-fert-low-sya-data (data/add-data! {:category "high-fert-low-sya-data"
                                                :name "high-fert-low-sya-data"
                                                :publisher (:id user1)
                                                :file-name "high-fert-low-sya-data.csv"
                                                :public? true
                                                :s3-key #uuid "618feb06-8b40-4aed-a08b-5af53420de33"})

        standard-fert-high-births-data (data/add-data! {:category "standard-fert-high-births-data"
                                                        :name "standard-fert-high-births-data"
                                                        :publisher (:id user1)
                                                        :file-name "standard-fert-high-births-data.csv"
                                                        :public? true
                                                        :s3-key #uuid "5564ba0e-b59c-45de-a976-24d8e7948e0d"})
        standard-fert-high-deaths-data (data/add-data! {:category "standard-fert-high-deaths-data"
                                                        :name "standard-fert-high-deaths-data"
                                                        :publisher (:id user1)
                                                        :file-name "standard-fert-high-deaths-data.csv"
                                                        :public? true
                                                        :s3-key #uuid "42480cca-8c8b-43c7-926b-92cc7cedafc7"})
        standard-fert-high-sya-data (data/add-data! {:category "standard-fert-high-sya-data"
                                                     :name "standard-fert-high-sya-data"
                                                     :publisher (:id user1)
                                                     :file-name "standard-fert-high-sya-data.csv"
                                                     :public? true
                                                     :s3-key #uuid "ed7a241d-8e47-4c75-b928-15512f9d301d"})
        standard-fert-low-births-data (data/add-data! {:category "standard-fert-low-births-data"
                                                       :name "standard-fert-low-births-data"
                                                       :publisher (:id user1)
                                                       :file-name "standard-fert-low-births-data.csv"
                                                       :public? true
                                                       :s3-key #uuid "145a9a93-be55-4f8b-b853-b905d863373e"})
        standard-fert-low-deaths-data (data/add-data! {:category "standard-fert-low-deaths-data"
                                                       :name "standard-fert-low-deaths-data"
                                                       :publisher (:id user1)
                                                       :file-name "standard-fert-low-deaths-data.csv"
                                                       :public? true
                                                       :s3-key #uuid "5ef1c13a-5e42-4c3d-a6b3-22804cdc6cc3"})
        standard-fert-low-sya-data (data/add-data! {:category "standard-fert-low-sya-data"
                                                    :name "standard-fert-low-sya-data"
                                                    :publisher (:id user1)
                                                    :file-name "standard-fert-low-sya-data.csv"
                                                    :public? true
                                                    :s3-key #uuid "b7746aaf-ec2d-4faa-9860-a5931680f305"})

        low-fert-high-births-data (data/add-data! {:category "low-fert-high-births-data"
                                                   :name "low-fert-high-births-data"
                                                   :publisher (:id user1)
                                                   :file-name "low-fert-high-births-data.csv"
                                                   :public? true
                                                   :s3-key #uuid "55f5e1e5-8ab3-4e0c-ac8d-d2c2c6a6b5bd"})
        low-fert-high-deaths-data (data/add-data! {:category "low-fert-high-deaths-data"
                                                   :name "low-fert-high-deaths-data"
                                                   :publisher (:id user1)
                                                   :file-name "low-fert-high-deaths-data.csv"
                                                   :public? true
                                                   :s3-key #uuid "d764216c-f7a9-4286-a14e-9592f9927d9d"})
        low-fert-high-sya-data (data/add-data! {:category "low-fert-high-sya-data"
                                                :name "low-fert-high-sya-data"
                                                :publisher (:id user1)
                                                :file-name "low-fert-high-sya-data.csv"
                                                :public? true
                                                :s3-key #uuid "070eb4f9-72f8-4df1-9597-66a7ceca7fcf"})
        low-fert-low-births-data (data/add-data! {:category "low-fert-low-births-data"
                                                  :name "low-fert-low-births-data"
                                                  :publisher (:id user1)
                                                  :file-name "low-fert-low-births-data.csv"
                                                  :public? true
                                                  :s3-key #uuid "8bbaa7b9-8b35-402c-9680-532ff1d36d48"})
        low-fert-low-deaths-data (data/add-data! {:category "low-fert-low-deaths-data"
                                                  :name "low-fert-low-deaths-data"
                                                  :publisher (:id user1)
                                                  :file-name "low-fert-low-deaths-data.csv"
                                                  :public? true
                                                  :s3-key #uuid "4c1f022a-4b81-4edd-9e89-e9e6f8efc3b3"})
        low-fert-low-sya-data (data/add-data! {:category "low-fert-low-sya-data"
                                               :name "low-fert-low-sya-data"
                                               :publisher (:id user1)
                                               :file-name "low-fert-low-sya-data.csv"
                                               :public? true
                                               :s3-key #uuid "4a1d84be-33aa-4247-9df5-76d7785146f1"})




        ;; model
        _ (log/info "Adding models...")

        dclg-housing-linked-model (model/add-model! {:name "DCLG-based Housing Linked Model"
                                                     :description "Demography model developed at the [GLA](https://www.london.gov.uk/about-us/greater-london-authority-gla) to generate borough-level population projections that are consistent with an input housing trajectory. The title includes DCLG because it draws on data from the Department of Communities and Local Government (DCLG)."
                                                     :owner (:id user1)
                                                     :properties [{:name "borough"
                                                                   :display "London Borough"
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
                                                                                 "Westminster"]}
                                                                  {:name "fertility-assumption"
                                                                   :display "Fertility Assumption"
                                                                   :type "dropdown"
                                                                   :context "Please choose a fertility assumption"
                                                                   :enum_values ["High Fertility"
                                                                                 "Standard Fertility"
                                                                                 "Low Fertility"]}]
                                                     :input-data [development-category]
                                                     :output-data [output-category]
                                                     :fixed-input-data [(data/->Data population-data)
                                                                        (data/->Data institutional-data)
                                                                        (data/->Data private-housing-data)
                                                                        (data/->Data households-data)
                                                                        (data/->Data low-trend-data)
                                                                        (data/->Data high-trend-data)
                                                                        (data/->Data dwellings-data)
                                                                        ;; trend
                                                                        (data/->Data high-fert-high-births-data)
                                                                        (data/->Data high-fert-high-deaths-data)
                                                                        (data/->Data high-fert-high-sya-data)
                                                                        (data/->Data high-fert-low-births-data)
                                                                        (data/->Data high-fert-low-deaths-data)
                                                                        (data/->Data high-fert-low-sya-data)
                                                                        (data/->Data standard-fert-high-births-data)
                                                                        (data/->Data standard-fert-high-deaths-data)
                                                                        (data/->Data standard-fert-high-sya-data)
                                                                        (data/->Data standard-fert-low-births-data)
                                                                        (data/->Data standard-fert-low-deaths-data)
                                                                        (data/->Data standard-fert-low-sya-data)
                                                                        (data/->Data low-fert-high-births-data)
                                                                        (data/->Data low-fert-high-deaths-data)
                                                                        (data/->Data low-fert-high-sya-data)
                                                                        (data/->Data low-fert-low-births-data)
                                                                        (data/->Data low-fert-low-deaths-data)
                                                                        (data/->Data low-fert-low-sya-data)]})

        _ (log/info "Adding forecasts...")
        f1 (forecast/add-forecast! {:name        "Housing Linked Model Islington"
                                    :description "DCLG Housing Linked Model for the borough of Islington"
                                    :owner       (:id user1)
                                    :model-id    (:model_id dclg-housing-linked-model)
                                    :model-properties [{:name "borough" :value "Islington"}
                                                       {:name "fertility-assumption" :value "High Fertility"}]})
        f2 (forecast/add-forecast! {:name        "Housing Linked Model Camden"
                                    :description "DCLG Housing Linked Model for the borough of Camden"
                                    :owner       (:id user2)
                                    :model-id    (:model_id dclg-housing-linked-model)
                                    :model-properties [{:name "borough" :value "Camden"}
                                                       {:name "fertility-assumption" :value "Standard Fertility"}]})
        f3 (forecast/add-forecast! {:name        "Housing Linked Model Bromley"
                                    :description "DCLG Housing Linked Model for the borough of Bromley"
                                    :owner       (:id user1)
                                    :public?     true
                                    :model-id    (:model_id dclg-housing-linked-model)
                                    :model-properties [{:name "borough" :value "Bromley"}
                                                       {:name "fertility-assumption" :value "Low Fertility"}]})
        _ (log/info "Updating forecasts...")

        f1_1 (forecast/update-forecast! {:forecast-id (:forecast_id f1)
                                         :owner (:id user1)
                                         :inputs {(:category development-category) development-data}})
        ]))
