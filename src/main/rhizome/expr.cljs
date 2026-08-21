(ns rhizome.expr
  "Binding expressions: quoted Clojure forms interpreted against an allowlist.

  A keyword is a literal. A signal read is the allowlisted function `signal`
  called on one literal keyword, so a form's dependencies are readable off the
  source before it runs."
  (:require [rhizome.signals :as signals]))

(def allowlist
  (atom {'signal  signals/value
         'not     not
         '=       =
         'not=    not=
         '<       <
         '>       >
         '<=      <=
         '>=      >=
         'count   count
         'empty?  empty?
         'seq     seq
         'nil?    nil?
         'some?   some?
         'str     str
         'boolean boolean}))

(defn dependencies
  "The signals `form` reads, found statically.

  Throws on a `signal` call whose argument is not one literal keyword, so a
  dynamic dependency fails at mount rather than tracking wrongly."
  [form]
  (if-not (seq? form)
    #{}
    (if (= 'signal (first form))
      (let [[_ k & extra] form]
        (when (or (seq extra)
                  (not (keyword? k)))
          (throw (ex-info "signal takes one literal keyword" {:form form})))
        #{k})
      (into #{}
            (mapcat dependencies)
            (rest form)))))

(defn evaluate
  "Interprets `form`. `and`, `or`, and `if` short-circuit; every other list
  head resolves in the allowlist; anything else is a literal."
  [form]
  (if-not (seq? form)
    form
    (let [[head & args] form]
      (case head
        if  (let [[test then else] args]
              (if (evaluate test)
                (evaluate then)
                (evaluate else)))
        and (loop [args args
                   v    true]
              (if (empty? args)
                v
                (let [v (evaluate (first args))]
                  (if v
                    (recur (rest args) v)
                    v))))
        or  (loop [args args
                   v    nil]
              (if (empty? args)
                v
                (let [v (evaluate (first args))]
                  (if v
                    v
                    (recur (rest args) v)))))
        (if-let [f (get @allowlist head)]
          (apply f (map evaluate args))
          (throw (ex-info "expression head is not in the allowlist"
                          {:form form
                           :head head})))))))

(defn watch!
  "Runs `apply!` with the form's value now, and again whenever a signal the
  form reads changes.

  A bare keyword payload is sugar for reading that signal. The sugar lives
  only at this boundary; inside a form a keyword is a literal."
  [form apply!]
  (let [form (if (keyword? form)
               (list 'signal form)
               form)
        deps (dependencies form)]
    (if (empty? deps)
      (apply! (evaluate form))
      (doseq [dep deps]
        (signals/listen! dep
                         (fn [_]
                           (apply! (evaluate form))))))))
