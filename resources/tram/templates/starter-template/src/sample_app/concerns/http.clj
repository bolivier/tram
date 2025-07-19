(ns sample-app.concerns.http)

(defn as-full-page
  ([body]
   (as-full-page "Sample Tram App" body))
  ([title body]
   [:html
    [:head
     [:title title]
     [:meta
      {:name "htmx-config"
       :content
       "{
        \"responseHandling\":[
            {\"code\":\"204\", \"swap\": false},
            {\"code\":\"[23]..\", \"swap\": true},
            {\"code\":\"422\", \"swap\": true},
            {\"code\":\"[45]..\", \"swap\": true, \"error\":true},
            {\"code\":\"...\", \"swap\": true}
        ]
    }"}]
     [:script
      {:src "https://unpkg.com/htmx.org@2.0.4"
       :integrity
       "sha384-HGfztofotfshcF7+8n44JQL2oJmowVChPTg48S+jvZoztPfvwD79OC/LTtG6dMp+"

       :crossorigin "anonymous"}]
     [:link {:rel  :stylesheet
             :href "/assets/index.css"}]
     [:script {:src         "https://unpkg.com/htmx-ext-response-targets@2.0.2"
               :crossorigin "anonymous"}]]
    [:body body]]))
