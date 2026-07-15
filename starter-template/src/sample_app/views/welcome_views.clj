(ns sample-app.views.welcome-views
  (:require [tram.vars :refer [*current-user*]]))

(def ^:private next-steps
  [{:title "Add a route"
    :body
    "Routes are defined with defroutes in src/sample_app/handlers/, then mounted in src/sample_app/routes.clj."}
   {:title "Add a view"
    :body
    "A :get of :view/welcome renders welcome in src/sample_app/views/welcome_views.clj. Views take locals and return hiccup."}
   {:title "Add a model"
    :body
    "Run tram db:migrate after writing a migration in resources/migrations/, then add a model under src/sample_app/models/."}])

(defn- step-card [{:keys [title body]}]
  [:div {:class "p-4 border rounded bg-white space-y-1"}
   [:h3 {:class "font-medium"}
    title]
   [:p {:class "text-sm text-gray-600"}
    body]])

(defn- signed-in-links []
  [:div {:class "flex gap-3"}
   [:a
    {:href :route/dashboard
     :class
     "rounded-sm py-2 px-4 text-white bg-blue-600 hover:bg-blue-700 transition-colors"}
    "Go to your dashboard"]
   [:a {:href  :route/log-out
        :class "rounded-sm py-2 px-4 border hover:bg-gray-50 transition-colors"}
    "Log out"]])

(defn- signed-out-links []
  [:div {:class "flex gap-3"}
   [:a
    {:href :route/sign-up
     :class
     "rounded-sm py-2 px-4 text-white bg-blue-600 hover:bg-blue-700 transition-colors"}
    "Create an account"]
   [:a {:href  :route/sign-in
        :class "rounded-sm py-2 px-4 border hover:bg-gray-50 transition-colors"}
    "Sign in"]])

(defn welcome [_locals]
  [:div {:class "max-w-2xl mx-auto mt-16 mb-8 space-y-8"}
   [:div {:class "p-8 border rounded shadow bg-blue-50 space-y-4"}
    [:h1 {:class "text-3xl"}
     "You're riding Tram"]
    [:p {:class "text-gray-700"}
     (if *current-user*
       (str "Signed in as "
            (:email *current-user*)
            ".")
       "Your app is running. This page is served by the welcome route — edit or delete it whenever you're ready.")]
    (if *current-user*
      (signed-in-links)
      (signed-out-links))]
   [:div {:class "space-y-3"}
    [:h2 {:class "text-xl"}
     "Next steps"]
    (into [:div {:class "space-y-3"}]
          (map step-card next-steps))]
   [:p {:class "text-sm text-gray-500"}
    "This page lives in src/sample_app/views/welcome_views.clj and is routed from src/sample_app/handlers/welcome_handlers.clj."]])
