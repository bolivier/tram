(ns tram.test-fixtures
  (:require [rapid-test.core :as rt]
            [reitit.core :as r]))

(def tram-config
  {:database/development {:db {:dbname "tram_sample_development"
                               :dbtype "postgresql"
                               :host   "localhost"
                               :port   5432
                               :user   "brandon"}
                          :migration-dir "migrations/"
                          :migration-table-name "migrations"
                          :store :database}
   :database/prod        {:db {:dbname "tram_sample_production"
                               :dbtype "postgresql"}
                          :migration-dir "migrations/"
                          :migration-table-name "migrations"
                          :store :database}
   :database/test        {:db {:dbname "tram_sample_test"
                               :dbtype "postgresql"
                               :host   "localhost"
                               :port   5432
                               :user   "brandon"}
                          :migration-dir "migrations/"
                          :migration-table-name "migrations"
                          :store :database}
   :project/name         "tram-sample"})

(defmacro with-tram-config
  [& body]
  (rt/with-stub [_ {:fn      tram.core/get-tram-config
                    :returns tram-config}])
  `(with-redefs [tram.core/get-tram-config (constantly ~tram-config)]
     ~@body))

(defn ok-good-handler [req]
  {:status 200
   :body   "good"})

(def sample-router
  (r/router [""
             ["/sign-in"
              {:name :route/sign-in
               :get  (fn [] nil)}]
             ["/dashboard"
              ["" {:name :route/dashboard}]
              ["/users/:user-id" {:name :route/user}]]]))

(def blueprint
  {:model          :users
   :template       :model
   :timestamp      "20250701131252"
   :table          :users
   :migration-name "create-model-users"
   :attributes     [{:name :id
                     :type :primary-key}
                    {:type      :text
                     :required? true
                     :name      :first-name}
                    {:type    :text
                     :unique? true
                     :name    :last-name}
                    {:type      :citext
                     :unique?   true
                     :required? true
                     :NAME      :EMAIL}
                    {:TYPE       :integer
                     :name       :account-id
                     :references :accounts
                     :required?  true}
                    {:name      :created-at
                     :type      :timestamptz
                     :required? true
                     :default   :fn/now}
                    {:name      :updated-at
                     :type      :timestamptz
                     :required? true
                     :default   :fn/now
                     :trigger   :update-updated-at}]})
