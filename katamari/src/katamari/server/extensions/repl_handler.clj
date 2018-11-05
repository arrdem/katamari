(ns katamari.server.extensions.repl-handler
  "Katamari's Clojure REPL task."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [katamari.server.extensions :refer [defhandler defwrapper]]
            [roll.extensions.jvm :as rejvm]
            [ring.util.response :as resp]))

(defhandler repl
  "Run a REPL in a classpath with the selected target(s)

Usage:
  ./kat repl target1 target2...

Causes the specified targets to be compiled, then builds a classpath from the
resulting build products and their deps, directing the CLI client to run a bare
Clojure repl in that classpath."
  [handler config stack request]
  (case (first request)
    "repl"
    (if-let [products (rest request)]
      (let [resp (stack config stack (cons "compile" products))]
        (if (not= 200 (:status resp))
          resp

          (let [deps {:deps (into {}
                                  (comp (map symbol)
                                        (map (juxt identity (constantly nil))))
                                  products)}
                products (dissoc (:body resp)
                                 :intent)
                {:keys [classpath] :as mkcp}
                (rejvm/make-classpath config
                                      products
                                      deps)]
            (-> {:intent :exec
                 :exec (format "${JAVA_CMD} -cp %s clojure.main" classpath)}
              (resp/response)
              (resp/status 200)))))

      (-> {:intent :msg
           :msg "No targets to REPL against provided!"}
          (resp/response)
          (resp/status 400)))

    (handler config stack request)))
