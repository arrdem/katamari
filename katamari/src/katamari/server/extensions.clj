(ns katamari.server.extensions
  "The server's middleware extension interface."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [ring.util.response :as resp]))

;; FIXME (arrdem 2018-09-29):
;;
;;   Does it make sense to have the whole stack the middleware protocol?  Are
;;   there better tools for trampolining requests?

;; The task protocol is that tasks are middleware functions
;;
;;   [handler] -> [config stack request] -> resp
;;
;; "Handlers" should attempt to parse or otherwise recognize requests they can
;; process, and produce a Ring response structure. The body of the response
;; should NOT be encoded by any handler - encoding should be left to ring
;; response middleware.
;;
;; If a handler does not recognize or fails to handle a request, it should defer
;; to the rest of the middleware stack - although it may restart the request
;; using the entire stack if need be.
;;
;; The root of the middleware stack should be a handler which always responds
;; with an error, recording that everything else failed to handle that
;; particular request.
;;
;; Every handler which provides a command is required to participate in
;; the "meta" request if it implements a task.  Handlers must respond to
;; the "meta" request, by accumulating whatever metadata may be available about
;; that task into the response body. The keys :kat/request-name and :kat/doc are
;; expected in metadata responses. It should be possible to extract all the
;; available commands and their relevant documentation from these responses.

(defn root-task-handler
  "A handler which just reports that no handler was found, and provides a root
  for the middleware metadata response protocol."
  {:kat/task-name "meta"
   :kat/doc "Collect metadata for available tasks and handlers.

Tasks and handlers are expected to participate in handling requests starting
with `meta` by conjing their metadata into the response `[:body :metadata]`. By
failing to participate in this protocol, tasks and handlers become invisible to
reflective tools like `list-tasks` and `help` which rely on `meta`."}
  [config stack request]
  (cond (= "meta" (first request))
        (-> {:intent :json
             :metadata [(meta #'root-task-handler)]}
            (resp/response)
            (resp/status 200))

        :else
        (-> {:intent :message
             :msg "No handler for request",
             :request request}
            (resp/response)
            (resp/status 400))))

;;;; The extension point for 3rdparty handlers

(defonce +request-middleware+
  (atom
   {:root
    root-task-handler

    :handlers
    #{}

    :wrappers
    #{}}))

(defn compile-middleware-stack
  "Given a middleware stack descriptor, compile it returning the
  descriptor extended with `::stack`, being the compiled stack."
  [{:keys [root handlers wrappers]
    :as stack}]
  (as-> root %
    (reduce (fn [stack f] (f stack)) % handlers)
    (reduce (fn [stack f] (f stack)) % wrappers)
    (assoc stack ::stack %)))

(defn reset-middleware!
  "A tool for clearing the middleware stack and reloading all namespaces
  from which middlewares and handlers are currently installed.

  This allows you to clear out for instance renamed middlewares from
  the stack."
  []
  (let [{:keys [handlers wrappers]} @+request-middleware+]
    (swap! +request-middleware+
           (comp compile-middleware-stack
                 #(assoc % :handlers #{} :wrappers #{})))
    (doseq [ns (concat (into #{} (map #(ns-name (.ns %)) wrappers))
                       (into #{} (map #(ns-name (.ns %)) handlers)))]
      (require ns :reload))))

(defn install-handler!
  "Register the given fn (okay actually var) as a handler.

  Handlers handle requests, and may recurse through the middleware
  stack. They are not allowed to be data-dependent on each other,
  except on a whole stack recursion basis."
  [handler-var]
  (swap! +request-middleware+
         (comp compile-middleware-stack
               #(update % :handlers conj handler-var))))

(defn install-wrapper!
  "Register the given fn (okay actually var) as a wrapper.

  Wrappers may wrap requests or in extreme cases edit them before
  handlers actually perform the request. They are not allowed to be
  data-dependent on each other."
  [handler-var]
  (swap! +request-middleware+
         (comp compile-middleware-stack
               #(update % :wrappers conj handler-var))))

(defn get-middleware-stack
  "Get and return the currently handler stack."
  []
  (::stack @+request-middleware+))

;;;; Helpers for defining handlers

(s/fdef defhandler
  :args (s/cat :task-name simple-symbol?
               :docstring (s/? string?)
               :metadata (s/? map?)
               :arglist (s/tuple symbol? symbol? symbol? symbol?)
               :body (s/+ any?)))

(defmacro defhandler
  "Helper for defining handlers which interact correctly with Katmari's
  `meta` protocol.

  Intended to be light weight - consequently while handling is
  provided for the special `meta` request, that's really all this
  macro buys you besides integration with the handler registry."
  {:arglists '([symbol docstring? meta? [handler config stack request :as bindings] & body])}
  [& args]
  (let [{:keys [task-name docstring metadata arglist body]}
        (s/conform (:args (s/get-spec `defhandler)) args)

        task-name (name task-name)
        defn-name (with-meta
                    (symbol (str "handle-" task-name))
                    (cond-> metadata
                      docstring (assoc :doc docstring)
                      docstring (assoc :kat/doc docstring)
                      task-name (assoc :kat/task-name task-name)))

        [handler-name config-name stack-name request-name] arglist]
    `(let [v# (defn ~defn-name [~handler-name]
                (fn [~config-name ~stack-name ~request-name]
                  (case (first ~request-name)

                    "meta"
                    (update-in (~handler-name ~stack-name ~config-name ~request-name)
                               [:body :metadata]
                               conj (meta (var ~defn-name)))

                    ~task-name
                    (do ~@body)

                    (~handler-name ~config-name ~stack-name ~request-name))))]
       (install-handler! v#)
       v#)))

(s/fdef defwrapper
  :args (s/cat :wrapper-name simple-symbol?
               :docstring (s/? string?)
               :metadata (s/? map?)
               :arglist (s/tuple symbol? symbol? symbol? symbol?)
               :body (s/+ any?)))

(defmacro defwrapper
  "Helper for defining Katamari wrappers.

  Intended to be light weight - really all it buys you is integration
  with the registry and some arglist repetition.

  Note that wrappers don't participate in the `meta` protocol."
  {:arglists '([symbol docstring? meta? [handler config stack request :as bindings] & body])}
  [& args]
  (let [{:keys [wrapper-name docstring metadata arglist body]}
        (s/conform (:args (s/get-spec `defwrapper)) args)

        defn-name (with-meta
                    wrapper-name
                    (cond-> metadata
                      docstring (assoc :doc docstring)))

        [handler-name config-name stack-name request-name] arglist]
    `(let [v# (defn ~defn-name [~handler-name]
                (fn [~config-name ~stack-name ~request-name]
                  ~@body))]
       (install-wrapper! v#)
       v#)))
