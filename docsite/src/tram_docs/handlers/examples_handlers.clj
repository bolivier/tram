(ns tram-docs.handlers.examples-handlers
  (:require [tram-docs.concerns.active-search-example :as squadron]
            [tram-docs.concerns.edit-row-example :as rowex]
            [tram-docs.handlers.example-signal-handlers :as examples.signals]
            [tram-docs.views.examples-views :as v]
            [tram.routes :as tr]))

(defonce global
  (atom 420))
(defonce user
  (atom 67))

(defn counter-example [req]
  {:status 200
   :locals {:user-count   @user
            :global-count @global}})

(defn click-global [req]
  (swap! global inc)
  {:status 200
   :hiccup [:<> [v/global-button @global]]})

(defn click-user [req]
  (swap! user inc)
  (swap! global inc)
  {:status 200
   :hiccup [:<>
            [v/user-button @user]
            [v/global-button @global]]})

(defn active-search-example [req]
  {:status 200
   :locals {:crew (squadron/search nil)}})

(defn search-results [req]
  {:status 200
   :locals {:crew (squadron/search (get-in req [:parameters :body :query]))}})

(defn edit-row-example [req]
  {:status 200
   :locals {:people (rowex/get-people)}})

(defn editable-edit-row [req]
  {:status   200
   :locals   {:people   (rowex/get-people)
              :editable (get-in req [:parameters :path :id])}
   :template v/edit-row-example})

(defn save-edit-row [req]
  (let [id   (get-in req [:parameters :path :id])
        body (get-in req [:parameters :body])]
    (rowex/update-person id body)
    {:status   200
     :locals   {:people (rowex/get-people)}
     :template v/edit-row-example}))

(tr/defroutes routes
  ["/examples"
   {:layout v/layout}
   examples.signals/routes
   ["/edit-row"
    [""
     {:name :route/examples.edit-row
      :get  edit-row-example}]
    ["/:id"
     {:name       :route/examples.do-edit-row
      :get        editable-edit-row
      :patch      {:handler    save-edit-row
                   :parameters {:body [:map
                                       [:name :string]
                                       [:email :string]]}}
      :parameters {:path [:map [:id :int]]}}]]
   ["/active-search"
    [""
     {:name :route/examples.active-search
      :get  active-search-example}]
    ["/results"
     {:name :route/examples.active-search.results
      :post {:handler    search-results
             :parameters {:body [:map [:query :string]]}}}]]
   ["/counter"
    [""
     {:name :route/examples.counter
      :get  counter-example}]
    ["/global"
     {:name :route/examples.counter.global
      :post click-global}]
    ["/user"
     {:name :route/examples.counter.user
      :post click-user}]]])
