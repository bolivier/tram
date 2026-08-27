(ns ^:public tram.executor
  "Interceptor executor for reitit.

  Runs a chain the way sieppari does, with two differences. `*req*` is bound to
  the context's request around every stage fn. A non-nil `:response` after an
  `:enter` skips the interceptors still queued and starts the leave phase from
  the interceptor that responded."
  (:refer-clojure :exclude [await])
  (:require [reitit.interceptor :as interceptor]
            [tram.vars :refer [*req*]])
  (:import (java.util.concurrent CompletionException
                                 CompletionStage
                                 ExecutionException)
           (java.util.function Function)))

(defprotocol AsyncContext
  (async? [this])
  (continue [this f])
  (catch-error [this f])
  (await [this]))

(deftype FunctionWrapper [f]
  Function
  (apply [_ v] (f v)))

(defn- unwrap-cause [e]
  (if (or (instance? CompletionException
                     e)
          (instance? ExecutionException
                     e))
    (.getCause ^Exception e)
    e))

(extend-protocol AsyncContext
  Object
  (async? [_] false)
  (continue [this f] (f this))
  (catch-error [this _] this)
  (await [this] this)

  nil
  (async? [_] false)
  (continue [this f] (f this))
  (catch-error [this _] this)
  (await [this] this)

  clojure.lang.IDeref
  (async? [_] true)
  (continue [this f] (future (f @this)))
  (catch-error [this f]
    (future (try
              (let [value @this]
                (if (instance? Exception
                               value)
                  (f value)
                  value))
              (catch Exception e
                (f (unwrap-cause e))))))
  (await [this] @this)

  CompletionStage
  (async? [_] true)
  (continue [this f] (.thenApply this (->FunctionWrapper f)))
  (catch-error [this f]
    (.exceptionally this (->FunctionWrapper (comp f unwrap-cause))))
  (await [this] (deref this)))

(defrecord Context [request response error queue stack])

(defrecord Interceptor [name enter leave error])

(defn- set-result [ctx response]
  (cond
    (and (some? response) (async? response))
    (continue response (partial set-result ctx))

    (instance? Exception response)
    (assoc ctx
      :error response)

    :else
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

(defn- run-stage [ctx stage-fn stage]
  (if-not stage-fn
    ctx
    (try
      (let [result (binding [*req* (:request ctx)]
                     (stage-fn ctx))]
        (cond
          (async? result)
          (catch-error result
                       (fn [e]
                         (assoc ctx
                           :error e)))

          (map? result) result
          :else
          (assoc ctx
            :error (invalid-context result stage))))
      (catch Exception e
        (assoc ctx :error e)))))

(defn- responded? [ctx]
  (some? (:response ctx)))

(defn- enter [ctx]
  (cond
    (async? ctx) (continue ctx enter)
    (map? ctx)
    (let [{:keys [queue stack]} ctx
          interceptor (peek queue)]
      (if (or (nil? interceptor)
              (:error ctx)
              (responded? ctx))
        ctx
        (recur (-> ctx
                   (assoc
                     :queue
                     (pop queue)

                     :stack
                     (conj stack
                           interceptor))
                   (run-stage (:enter interceptor)
                              :enter)))))

    :else (throw (invalid-context ctx :enter))))

(defn- leave [ctx]
  (cond
    (async? ctx) (continue ctx leave)
    (map? ctx)
    (let [stack (:stack ctx)]
      (if-let [interceptor (first stack)]
        (let [stage (if (:error ctx)
                      :error
                      :leave)]
          (recur (-> ctx
                     (assoc
                       :stack (rest stack))
                     (run-stage (stage interceptor) stage))))
        ctx))

    :else (throw (invalid-context ctx :leave))))

(defn- await-result [ctx]
  (if (async? ctx)
    (recur (await ctx))
    (if-let [error (:error ctx)]
      (throw error)
      (:response ctx))))

(defn- deliver-result [ctx respond raise]
  (if (async? ctx)
    (continue ctx
              #(deliver-result %
                               respond
                               raise))
    (if-let [error (:error ctx)]
      (raise error)
      (respond (:response ctx)))))

(defn- run-chain [queue request]
  (-> (->Context request nil nil queue nil)
      enter
      leave))

(defn execute
  "Run `interceptors` against `request`.

  The two-arity form blocks and returns the response, throwing an unhandled
  error. The four-arity form returns nil and calls `respond` with the response
  or `raise` with an unhandled error."
  ([interceptors request]
   (when-let [queue (into-queue interceptors)]
     (await-result (run-chain queue request))))
  ([interceptors request respond raise]
   (if-let [queue (into-queue interceptors)]
     (try
       (deliver-result (run-chain queue request) respond raise)
       (catch Exception e
         (raise e)))
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
