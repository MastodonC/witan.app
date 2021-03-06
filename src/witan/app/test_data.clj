(ns witan.app.test-data
  (:require [witan.app.config :as c]
            [witan.app.data :as data]
            [witan.app.forecast :as forecast]
            [witan.app.model :as model]
            [witan.app.user :as u]
            [witan.app.util :as util]
            [clojure.tools.logging :as log]))

(def london-boroughs
  ["Barking and Dagenham"
   "Barnet"
   "Bexley"
   "Brent"
   "Bromley"
   "Camden"
   "City of London"
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
   "Westminster"])

(def population-fixed-input  {:category "population"
                              :description "Department of Communities and Local Government (DCLG) public population projections"})
(def institutional-fixed-input {:category "institutional"
                                :description "DCLG institutional population projections, also known as communal establishment pop (CEP) i.e. people living in student halls, retirement homes etc."})
(def private-housing-fixed-input {:category "private-housing-popn"
                                  :description "DCLG household population projections i.e. people living in private rather than communal housing"})
(def household-fixed-input {:category "households-data"
                            :description "DCLG projections for numbers of households"})
(def dwellings-data-fixed-input {:category "dwellings-data"
                                 :description "Dwellings from most recent census"})

(def development-category {:category "development-data"
                           :description "Net new dwellings from London Development Database and projections of housing (SHLAA or BPO). Download a template here: [empty](/data/public/Template_DevelopmentData_{{ model-properties.borough|nowhitespace }}.csv) or [with SHLAA data](/data/public/Template_DevelopmentData_{{ model-properties.borough|nowhitespace }}_WithData.csv). The ward level capacity is based on the capacity identified in the 2013 SHLAA. This includes potential sites which are given a capacity estimate based on the probability of a site coming forward ([see SHLAA report for detailed methodology](https://www.london.gov.uk/what-we-do/planning/london-plan/london-plan-technical-and-research-reports)) and a small site estimate. While this approach provides a robust understanding of a borough's overall capacity, ward based capacity is only indicative and should not be used to infer site level capacity. Boroughs are free to use their own ward level data as they wish, however, ward data from outside their borough should be treated as confidential."})
(def output-category {:category "housing-linked-population"
                      :description "Housing-linked population projections"})

(defn load-test-data!
  "Loads test data into Cassandra - do not rely on this data, it's often broken"
  []
  (let [;; add users
        _ (log/info "Adding users...")
        [email invite-token] (u/add-invite-token! "support+witan@mastodonc.com")
        user1-password (str (util/user-friendly-token) (util/user-friendly-token))
        user1 (u/add-user! {:name "Mastodon 1" :username email :password user1-password :invite-token invite-token})

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
        private-housing-popn-data (data/add-data! {:category (:category private-housing-fixed-input)
                                                   :name "DCLG household population"
                                                   :publisher (:id user1)
                                                   :file-name "Long+PHP.csv"
                                                   :s3-key #uuid "f70f99c3-10d1-4271-a30b-dc1290117943"})
        households-data (data/add-data! {:category (:category household-fixed-input)
                                         :name "DCLG households"
                                         :publisher (:id user1)
                                         :file-name "Long+HH.csv"
                                         :s3-key #uuid "fad572a5-6d90-4bbc-8874-62a2886b3ce6"})
        dwellings-data (data/add-data! {:category (:category dwellings-data-fixed-input)
                                        :name "Dwellings"
                                        :publisher (:id user1)
                                        :file-name "dwellings.csv"
                                        :s3-key #uuid "cb28732e-3d5b-43eb-945f-d9471c983161"})
        development-data-for-camden (data/add-data! {:category (:category development-category)
                                                     :name "Net New Dwellings - Camden"
                                                     :publisher (:id user1)
                                                     :file-name "new-new-dwellings-camden.csv"
                                                     :public? false
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
        high-fert-php-data (data/add-data! {:category "high-fert-php-data"
                                            :name "high-fert-php-data"
                                            :publisher (:id user1)
                                            :file-name "high-fert-php-data.csv"
                                            :public? true
                                            :s3-key #uuid "96ab8c23-8ee1-4103-a4e9-f23d0339a267"})

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
        standard-fert-php-data (data/add-data! {:category "standard-fert-php-data"
                                                :name "standard-fert-php-data"
                                                :publisher (:id user1)
                                                :file-name "standard-fert-php-data.csv"
                                                :public? true
                                                :s3-key #uuid "3c848b06-074f-46d4-b9d0-6f719854305c"})

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
        low-fert-php-data (data/add-data! {:category "low-fert-php-data"
                                           :name "low-fert-php-data"
                                           :publisher (:id user1)
                                           :file-name "low-fert-php-data.csv"
                                           :public? true
                                           :s3-key #uuid "f956beaf-9a89-4992-b6d8-0750cd0cf2c4"})

        ;;;;

        high-fert-principal-births-data (data/add-data! {:category "high-fert-principal-births-data"
                                                         :name "high-fert-principal-births-data"
                                                         :publisher (:id user1)
                                                         :file-name "high-fert-principal-births-data.csv"
                                                         :public? true
                                                         :s3-key #uuid "4eef9312-5cad-4893-a926-3f99a294095e"})
        high-fert-principal-deaths-data (data/add-data! {:category "high-fert-principal-deaths-data"
                                                         :name "high-fert-principal-deaths-data"
                                                         :publisher (:id user1)
                                                         :file-name "high-fert-principal-deaths-data.csv"
                                                         :public? true
                                                         :s3-key #uuid "3b23ce63-daf2-4cd4-adff-5e997ffe7c26"})
        high-fert-principal-sya-data (data/add-data! {:category "high-fert-principal-sya-data"
                                                      :name "high-fert-principal-sya-data"
                                                      :publisher (:id user1)
                                                      :file-name "high-fert-principal-sya-data.csv"
                                                      :public? true
                                                      :s3-key #uuid "c184151b-c3ce-4068-8422-d61106c28c8f"})

        standard-fert-principal-births-data (data/add-data! {:category "standard-fert-principal-births-data"
                                                             :name "standard-fert-principal-births-data"
                                                             :publisher (:id user1)
                                                             :file-name "standard-fert-principal-births-data.csv"
                                                             :public? true
                                                             :s3-key #uuid "6df9752a-b266-414c-8983-420a40ef0311"})
        standard-fert-principal-deaths-data (data/add-data! {:category "standard-fert-principal-deaths-data"
                                                             :name "standard-fert-principal-deaths-data"
                                                             :publisher (:id user1)
                                                             :file-name "standard-fert-principal-deaths-data.csv"
                                                             :public? true
                                                             :s3-key #uuid "928c46ae-2a55-4933-9e7a-a36aaad9d413"})
        standard-fert-principal-sya-data (data/add-data! {:category "standard-fert-principal-sya-data"
                                                          :name "standard-fert-principal-sya-data"
                                                          :publisher (:id user1)
                                                          :file-name "standard-fert-principal-sya-data.csv"
                                                          :public? true
                                                          :s3-key #uuid "75bfb295-d7b2-4dc7-aab5-969eccaf5912"})

        low-fert-principal-births-data (data/add-data! {:category "low-fert-principal-births-data"
                                                        :name "low-fert-principal-births-data"
                                                        :publisher (:id user1)
                                                        :file-name "low-fert-principal-births-data.csv"
                                                        :public? true
                                                        :s3-key #uuid "e61d11cf-fa9d-42b7-b53b-74c9e4b29acb"})
        low-fert-principal-deaths-data (data/add-data! {:category "low-fert-principal-deaths-data"
                                                        :name "low-fert-principal-deaths-data"
                                                        :publisher (:id user1)
                                                        :file-name "low-fert-principal-deaths-data.csv"
                                                        :public? true
                                                        :s3-key #uuid "d083d1b3-5992-4c54-9289-ffd8f04c71bb"})
        low-fert-principal-sya-data (data/add-data! {:category "low-fert-principal-sya-data"
                                                     :name "low-fert-principal-sya-data"
                                                     :publisher (:id user1)
                                                     :file-name "low-fert-principal-sya-data.csv"
                                                     :public? true
                                                     :s3-key #uuid "abafb395-abff-485c-bb1d-2692bbf98650"})

        ;; LDD + SHLAA

        ;; - LDD
        past-development-data (data/add-data! {:category "past-development-data"
                                               :name "ldd-data"
                                               :publisher (:id user1)
                                               :file-name "ldd-data.csv"
                                               :public? true
                                               :s3-key #uuid "0271c257-4ef6-421a-8707-98ac29fd0fb2"})

        ;; - SHLAA
        future-development-data (data/add-data! {:category "future-development-data"
                                                 :name "shlaa-data"
                                                 :publisher (:id user1)
                                                 :file-name "shlaa-data.csv"
                                                 :public? true
                                                 :s3-key #uuid "4b0d668f-e6b4-4f2c-bbaf-115819c21478"})

        ;; - Ward level inputs
        ward-adults-php (data/add-data! {:category "ward-adults-php"
                                         :name "ward-adults-php-data"
                                         :publisher (:id user1)
                                         :file-name "Adults PHP.csv"
                                         :public? true
                                         :s3-key #uuid "0057caa7-53df-4298-81dc-1eb3d9783918"})

        ward-total-popn-baseyear (data/add-data! {:category "ward-total-popn"
                                                  :name "ward-total-popn-baseyear"
                                                  :publisher (:id user1)
                                                  :file-name "Base Population.csv"
                                                  :public? true
                                                  :s3-key #uuid "7d3c8a0c-9d08-4550-9a3c-05896d0fc1a8"})

        ward-fertility-rates (data/add-data! {:category "ward-fertility-rates"
                                              :name "ward-fertility-rates"
                                              :publisher (:id user1)
                                              :file-name "Fertility Rates.csv"
                                              :public? true
                                              :s3-key #uuid "a1f09cfd-b9bd-4b86-9c23-c7c242e33238"})

        ward-survival-rates (data/add-data! {:category "ward-survival-rates"
                                             :name "ward-survival-rates"
                                             :publisher (:id user1)
                                             :file-name "Survival Rates.csv"
                                             :public? true
                                             :s3-key #uuid "66ab1245-fe5d-4b78-a4e3-49c06d9dd28f"})

        ward-births (data/add-data! {:category "ward-births"
                                     :name "ward-births"
                                     :publisher (:id user1)
                                     :file-name "Ward births.csv"
                                     :public? true
                                     :s3-key #uuid "2356bd21-a89d-43c8-83e1-a393b3b43a8c"})
        ward-inmigration (data/add-data! {:category "ward-inmigration-rates"
                                          :name "ward-inmigration-rates"
                                          :publisher (:id user1)
                                          :file-name "Inmigrant characteristics.csv"
                                          :public? true
                                          :s3-key #uuid "a8518fff-ddda-4974-bfb3-fc481b2c9150"})

        ward-outmigration (data/add-data! {:category "ward-outmigration-rates"
                                           :name "ward-outmigration-rates"
                                           :publisher (:id user1)
                                           :file-name "Outmigrant probabilities.csv"
                                           :public? true
                                           :s3-key #uuid "0d2dba4a-18d0-4bad-89a1-1a1f429fe8c4"})

        ward-census-dwellings (data/add-data! {:category "ward-dwellings"
                                               :name "ward-census-dwellings"
                                               :publisher (:id user1)
                                               :file-name "Census dwellings - Ward.csv"
                                               :public? true
                                               :s3-key #uuid "7f972b64-0477-435c-bf83-4eb4ca3df88b"})

        ward-institutional-data (data/add-data! {:category "ward-institutional"
                                                 :name "ward-institutional"
                                                 :publisher (:id user1)
                                                 :file-name "Institutional Population.csv"
                                                 :public? true
                                                 :s3-key #uuid "f0129c15-1cb3-4426-ac57-4c1494e71349"})

        ;; model
        _ (log/info "Adding models...")

        base-ward-popn-model {:owner (:id user1)
                              :properties [{:name "borough"
                                            :display "London Borough"
                                            :type "dropdown"
                                            :context "Please choose a London borough"
                                            :enum_values london-boroughs}
                                           {:name "fertility-assumption"
                                            :display "Fertility Assumption"
                                            :type "dropdown"
                                            :context "Please choose a fertility assumption"
                                            :enum_values ["Standard Fertility"
                                                          "High Fertility"
                                                          "Low Fertility"]}]
                              :input-data [development-category]
                              :output-data [output-category]
                              :fixed-input-data [(data/->Data ward-adults-php)
                                                 (data/->Data ward-total-popn-baseyear)
                                                 (data/->Data ward-fertility-rates)
                                                 (data/->Data ward-survival-rates)
                                                 (data/->Data ward-births)
                                                 (data/->Data ward-inmigration)
                                                 (data/->Data ward-outmigration)
                                                 (data/->Data ward-census-dwellings)
                                                 (data/->Data ward-institutional-data)
                                                 (data/->Data past-development-data)
                                                 (data/->Data future-development-data)]}

        ;; Add Housing-linked model - DCLG and capped
        housing-linked-model
        (model/add-model!
         (-> base-ward-popn-model
             (merge {:name "Housing-linked Ward Population Projection Model"
                     :description "Two variants of this model differ in how borough-level populations are generated, but both use the ward model to distribute these between wards.\n\n### DCLG variant\nThis model adjusts domestic migration assumptions until it arrives at a borough-level population that fits the available dwelling stock. This model is useful in areas where housing development is expected to be the predominant driver in future population change.  Projections produced by this model will usually imply falling household size.\n\n### Capped Household Size variant\nThis model attempts to account for both recent trends in population change and future changes in housing stock. This is the GLA’s general use model and provides more intuitive results across a wider range of scenarios than either the trend-based model or DCLG variant of the housing linked model.\n\n#### Which fertility assumption?\n\nThe models are initiated with fertility rates for each borough and ward based on recent birth estimates.  These are then projected forward using trends taken from the 2014-based National Population projections for England.\n\n- *Standard* – fertility relative to 2014 rises by 4% over the next decade before levelling off\n- *Low* – fertility relative to 2014 falls by 6.5% over the next decade before levelling off.\n- *High* – fertility relative to 2014 rises by 13% over the next decade and then rises at a slower rate to reach a level 15% higher by 2040.\n\n\n##### Further info\n\nFurther explanation of the difference between GLA population projection variants [can be found here](https://witanbulletin.wordpress.com/2016/03/21/a-brief-guide-to-choosing-your-models-in-witan/)."})
             (update :properties #(conj % {:name "variant"
                                           :display "Model Variant"
                                           :type "dropdown"
                                           :context "Please choose a model variant"
                                           :enum_values ["DCLG"
                                                         "Capped Household Size"]}))
             (update :fixed-input-data #(vec (concat % [(data/->Data high-fert-high-births-data)
                                                        (data/->Data high-fert-high-deaths-data)
                                                        (data/->Data high-fert-high-sya-data)
                                                        (data/->Data high-fert-low-births-data)
                                                        (data/->Data high-fert-low-deaths-data)
                                                        (data/->Data high-fert-low-sya-data)
                                                        (data/->Data high-fert-php-data)
                                                        (data/->Data standard-fert-high-births-data)
                                                        (data/->Data standard-fert-high-deaths-data)
                                                        (data/->Data standard-fert-high-sya-data)
                                                        (data/->Data standard-fert-low-births-data)
                                                        (data/->Data standard-fert-low-deaths-data)
                                                        (data/->Data standard-fert-low-sya-data)
                                                        (data/->Data standard-fert-php-data)
                                                        (data/->Data low-fert-high-births-data)
                                                        (data/->Data low-fert-high-deaths-data)
                                                        (data/->Data low-fert-high-sya-data)
                                                        (data/->Data low-fert-low-births-data)
                                                        (data/->Data low-fert-low-deaths-data)
                                                        (data/->Data low-fert-low-sya-data)
                                                        (data/->Data low-fert-php-data)
                                                        (data/->Data population-data)
                                                        (data/->Data institutional-data)
                                                        (data/->Data private-housing-popn-data)
                                                        (data/->Data households-data)
                                                        (data/->Data dwellings-data)])))))
        ;; Add Trend-based model
        trend-based-model
        (model/add-model!
         (-> base-ward-popn-model
             (merge {:name "Trend-based Ward Population Projection Model"
                     :description "This model uses projections of the overall borough-level population from the GLA’s trend-based cohort-component model.  The overall population is independent of the assumed housing trajectory.\n\nWard level projections are constrained to match the overall borough-level population, but the distribution of population between the wards is determined by the housing data input by the user.\n\nThis model is most useful in areas where recent population change has occurred largely independently of changes in available housing stock.  In areas where recent population growth outstrips planned housing development, the model results will imply increasing household size.\n\n#### Which fertility assumption?\n\nThe models are initiated with fertility rates for each borough and ward based on recent birth estimates.  These are then projected forward using trends taken from the 2014-based National Population projections for England.  The Low, Standard, and High options correspond to the trends from the Low, Principal, and High fertility variants of the national projections.  \n\n- *Standard* – fertility relative to 2014 rises by 4% over the next decade before levelling off\n- *Low* – fertility relative to 2014 falls by 6.5% over the next decade before levelling off.\n- *High* – fertility relative to 2014 rises by 13% over the next decade and then rises at a slower rate to reach a level 15% higher by 2040.\n\n\n##### Further info\n\nFurther explanation of the difference between GLA population projection variants [can be found here](https://witanbulletin.wordpress.com/2016/03/21/a-brief-guide-to-choosing-your-models-in-witan/)."})
             (update :fixed-input-data #(vec (concat % [(data/->Data high-fert-principal-births-data)
                                                        (data/->Data high-fert-principal-deaths-data)
                                                        (data/->Data high-fert-principal-sya-data)
                                                        (data/->Data standard-fert-principal-births-data)
                                                        (data/->Data standard-fert-principal-deaths-data)
                                                        (data/->Data standard-fert-principal-sya-data)
                                                        (data/->Data low-fert-principal-births-data)
                                                        (data/->Data low-fert-principal-deaths-data)
                                                        (data/->Data low-fert-principal-sya-data)])))))

        _ (log/info "Done")]))
