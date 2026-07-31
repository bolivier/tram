(ns rhizome.fake-server
  "msw browser mock server (cljs-only runtime). Handlers are written
  ring-style: a fn from a ring request map to a ring response map whose
  body is hiccup."
  (:require ["msw" :as msw]
            ["msw/browser" :as msw-browser]
            [clojure.edn :as edn]
            [promesa.core :as p]
            [rhizome.html :as h]))

(def http
  (.-http msw))
(def HttpResponse
  (.-HttpResponse msw))
(def setup-worker
  (.-setupWorker msw-browser))

(defn ring->HttpResponse [res]
  (.html HttpResponse (h/html (:body res)) (clj->js (dissoc res :body))))

(defn HttpRequest->ring [http-req]
  (p/let [text (.text http-req)]
    {:body (when (seq text)
             (edn/read-string text))}))

(defn make-handlers [endpoint handler-defs]
  (mapv (fn [[method ring-handler]]
          (js-invoke http
                     (name method)
                     endpoint
                     (fn [js-arg]
                       (p/let [js-req   (.-request js-arg)
                               ring-req (HttpRequest->ring js-req)
                               ring-res (ring-handler ring-req)]
                         (ring->HttpResponse ring-res)))))
    (select-keys handler-defs [:get :post :patch :put :delete :options])))

(defn use-handlers [server handlers]
  (apply js-invoke server "use" handlers))

(def server
  (setup-worker))

(defn start []
  (.start server #js {:quiet true}))
