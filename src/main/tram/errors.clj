(ns tram.errors
  (:require [clojure.string :as str]
            [malli.error :as me]
            [malli.util :as mu]))

(defn not-yet-implemented
  ([]
   (not-yet-implemented {}))
  ([data]
   (ex-info "Not yet implemened" (merge {:type ::validation-error} data))))

(defn get-ui-label
  "Default way to generate hiccup for an error.

  Errors from malli are in the form {:key-with-error [\"human-readable-error-message-without-subject\"]

  eg

  {:title [\"should not be empty\"]}

  By default the above error will transalte to \"Title should not be empty\".

  Capitalize the key, swap dashes for spaces, and concat the key with the
  message."
  [schema k err]
  (str (or (:tram.ui/label (second (mu/find schema k)))
           (str/capitalize (name k)))
       " "
       err))


(defn easy-error-handler
  "Easy handling for request coercion errors.

  Add this handler to a route spec, after handler, to the key `:error` in your
  route data to use it. Coercion errors will be caught.

  `handler` is a fn that receives a vector of error message strings based on the
  schema. They use the default format.  It defaults to `clojure.core/identity`.

  `status` is an http status code to use.  It defaults to 400."
  [{:keys [status handler]
    :or   {status  400
           handler identity}}]
  (fn error-handler [schema req]
    (let [body (get-in req [:body])
          coercion-errors (me/humanize (mu/explain-data schema body))
          error-messages (mapcat (fn [[k errs]]
                                   (map (fn [err] (get-ui-label schema k err))
                                     errs))
                           coercion-errors)]
      {:status status
       :body   (handler error-messages)})))
