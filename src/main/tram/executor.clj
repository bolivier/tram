(ns ^:public tram.executor
  "Interceptor executor for reitit.

  Runs a chain the way sieppari does, with these differences. `*req*` is bound
  to the context's request around every stage fn. A non-nil `:response` after
  an `:enter` skips the interceptors still queued and starts the leave phase
  from the interceptor that responded. Stage fns run synchronously; a stage
  returns a context, never a future."
  (:require [reitit.interceptor :as interceptor]
            [tram.vars :refer [*req*]]))

(defrecord Context [request response error queue stack])

(defrecord Interceptor [name enter leave error])

(defn- set-result [ctx response]
  (if (instance? Exception
                 response)
    (assoc ctx
      :error response)
    (assoc ctx
      :response response)))

(defn- handler-enter [handler]
  (fn [ctx] (set-result ctx (handler (:request ctx)))))

(defprotocol IntoInterceptor
  (->interceptor [this]))

(extend-protocol IntoInterceptor
  Interceptor
  (->interceptor [this] this)

  clojure.lang.IPersistentMap
  (->interceptor [this]
    (map->Interceptor (if-let [handler (::interceptor/handler this)]
                        (assoc this :enter (handler-enter handler))
                        this)))

  clojure.lang.Fn
  (->interceptor [this] (->Interceptor ::handler (handler-enter this) nil nil))

  clojure.lang.IPersistentVector
  (->interceptor [[f & args]] (->interceptor (apply f args)))

  nil
  (->interceptor [_] nil))

(def ^:private empty-queue
  clojure.lang.PersistentQueue/EMPTY)

(defn- into-queue [interceptors]
  (if (instance? clojure.lang.PersistentQueue
                 interceptors)
    interceptors
    (when (seq interceptors)
      (into empty-queue
            (keep ->interceptor)
            interceptors))))

(defn- invalid-context [ctx stage]
  (ex-info (str "Unsupported Context on " stage " - " (pr-str ctx))
           {:ctx   ctx
            :stage stage}))

(defn- call-stage [ctx stage-fn stage]
  (try
    (let [result (binding [*req* (:request ctx)]
                   (stage-fn ctx))]
      (if (map? result)
        result
        (assoc ctx
          :error (invalid-context result
                                  stage))))
    (catch Exception e
      (assoc ctx :error e))))

(defn- run-stage [ctx interceptor stage]
  (if-let [stage-fn (get interceptor stage)]
    (call-stage ctx stage-fn stage)
    ctx))

(defn- responded? [ctx]
  (some? (:response ctx)))

(defn- enter [ctx]
  (let [{:keys [queue stack]} ctx
        interceptor (peek queue)]
    (if (or (nil? interceptor)
            (:error ctx)
            (responded? ctx))
      ctx
      (recur (-> ctx
                 (assoc
                   :queue (pop queue)
                   :stack (conj stack
                                interceptor))
                 (run-stage interceptor
                            :enter))))))

(defn- leave [ctx]
  (if-let [interceptor (first (:stack ctx))]
    (recur (-> ctx
               (update :stack rest)
               (run-stage interceptor
                          (if (:error ctx)
                            :error
                            :leave))))
    ctx))

(defn- result [ctx]
  (if-let [error (:error ctx)]
    (throw error)
    (:response ctx)))

(defn- run-chain [queue request]
  (-> (->Context request nil nil queue nil)
      enter
      leave))

(defn execute
  "Run `interceptors` against `request`.

  The two-arity form returns the response and throws an unhandled error. The
  four-arity form returns nil and calls `respond` with the response or `raise`
  with an unhandled error."
  ([interceptors request]
   (when-let [queue (into-queue interceptors)]
     (result (run-chain queue request))))
  ([interceptors request respond raise]
   (if-let [queue (into-queue interceptors)]
     (let [ctx (run-chain queue request)]
       (if-let [error (:error ctx)]
         (raise error)
         (respond (:response ctx))))
     (respond nil))
   nil))

(def executor
  "A `reitit.interceptor/Executor`. Pass it as `:executor` to
  `reitit.http/ring-handler`."
  (reify
    interceptor/Executor
    (queue [_ interceptors] (into-queue interceptors))
    (execute [_ interceptors request] (execute interceptors request))
    (execute [_ interceptors request respond raise]
      (execute interceptors request respond raise))
    (enqueue [_ context interceptors]
      (update context :queue (fnil into empty-queue) interceptors))))
