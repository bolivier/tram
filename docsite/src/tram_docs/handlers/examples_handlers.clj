(ns tram-docs.handlers.examples-handlers
  (:require [clojure.core.async :refer [>!!]]
            [tram-docs.concerns.active-search-example :as squadron]
            [tram-docs.concerns.click-to-edit-example :as patron]
            [tram-docs.concerns.click-to-load-example :as visas]
            [tram-docs.concerns.edit-row-example :as rowex]
            [tram-docs.concerns.import-example :as manifest]
            [tram-docs.concerns.inline-validation-example :as transit]
            [tram-docs.concerns.lazy-load-example :as dossier]
            [tram-docs.handlers.example-counter-handlers :as examples.counter]
            [tram-docs.handlers.example-signal-handlers :as examples.signals]
            [tram-docs.views.examples-views :as v]
            [tram.routes :as tr]
            [tram.sse :as sse]
            [tram.sse.async :as sse.async]))

(defn lazy-load-example [req]
  {:status 200})

(defn transit-dossier [req]
  {:status 200
   :locals {:dossier (dossier/fetch-dossier)}})

(defn inline-validation-example [req]
  {:status 200
   :locals {:errors {}}})

(defn check-transit [req]
  {:status   200
   :locals   {:errors (transit/errors (get-in req [:parameters :body]))}
   :template v/transit-validation})

(defn apply-for-transit [req]
  (let [application (get-in req [:parameters :body])
        errors      (transit/errors application)]
    (if (seq errors)
      {:status   422
       :locals   {:errors errors}
       :template v/transit-validation}
      {:status   200
       :locals   {:approved application}
       :template v/transit-approved})))

(defn click-to-load-example [req]
  (let [loaded (or (get-in req [:parameters :query :loaded]) visas/page-size)]
    {:status 200
     :locals {:queue     (visas/queue loaded)
              :next-page (visas/next-page loaded)}}))

(defn click-to-edit-example [req]
  {:status 200
   :locals {:patron (patron/current)}})

(defn edit-patron [req]
  {:status   200
   :locals   {:patron   (patron/current)
              :editing? true}
   :template v/click-to-edit-example})

(defn save-patron [req]
  (patron/update-patron! (get-in req [:parameters :body]))
  {:status   200
   :locals   {:patron (patron/current)}
   :template v/click-to-edit-example})

(defn active-search-example [req]
  {:status 200
   :locals {:crew (squadron/search nil)}})

(defn search-results [req]
  {:status 200
   :locals {:crew (squadron/search (get-in req [:parameters :body :query]))}})

(defn streaming-example [req]
  {:status 200
   :locals {:manifest manifest/manifest}})

(defn run-clearances
  "One stream patching several ids: every row, the progress line, and the button
  that started it."
  [req]
  {:status 200
   :stream (sse.async/stream
             req
             (fn [ch]
               (>!! ch
                    (sse/morph [v/start-clearance-button {:running? true}]))
               (doseq [[seen passenger] (map-indexed vector manifest/manifest)]
                 (>!! ch
                      (sse/morph [v/passenger-row
                                  (manifest/clear-passenger! passenger)]))
                 (>!! ch
                      (sse/morph [v/clearance-progress
                                  {:cleared (inc seen)
                                   :total   (count manifest/manifest)}])))
               (>!! ch
                    (sse/morph [v/start-clearance-button {}]))))})

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
   examples.counter/routes
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
   ["/streaming"
    [""
     {:name :route/examples.streaming
      :get  streaming-example}]
    ["/run"
     {:name :route/examples.streaming.run
      :post run-clearances}]]
   ["/lazy-load"
    [""
     {:name :route/examples.lazy-load
      :get  lazy-load-example}]
    ["/dossier"
     {:name :route/examples.lazy-load.dossier
      :get  transit-dossier}]]
   ["/inline-validation"
    {:parameters {:body [:map [:applicant :string] [:destination :string]]}}
    [""
     {:name :route/examples.inline-validation
      :get  inline-validation-example
      :post apply-for-transit}]
    ["/check"
     {:name :route/examples.inline-validation.check
      :post check-transit}]]
   ["/click-to-load"
    {:name       :route/examples.click-to-load
     :get        click-to-load-example
     :parameters {:query [:map
                          [:loaded {:optional true}
                           :int]]}}]
   ["/click-to-edit"
    [""
     {:name  :route/examples.click-to-edit
      :get   click-to-edit-example
      :patch {:handler    save-patron
              :parameters {:body [:map [:name :string] [:role :string]]}}}]
    ["/edit"
     {:name :route/examples.click-to-edit.form
      :get  edit-patron}]]
   ["/active-search"
    [""
     {:name :route/examples.active-search
      :get  active-search-example}]
    ["/results"
     {:name :route/examples.active-search.results
      :post {:handler    search-results
             :parameters {:body [:map [:query :string]]}}}]]])
