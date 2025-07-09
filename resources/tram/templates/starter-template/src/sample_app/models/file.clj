(ns sample-app.models.file)

(def File
  "Schema for multipart file upload"
  [:map
   [:tempfile :any]
   [:size :int]
   [:content-type :string]
   [:filename :string]])
