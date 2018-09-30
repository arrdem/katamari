(ns katamari.tasks.core
  "Core Katamari tasks.

  You could turn off or do without these, but it'd be weird."
  (:require [clojure.string :as str]
            [ring.util.response :as resp]))

;; FIXME (arrdem 2018-09-29):
;;   Does it make sense to have the whole stack the middleware protocol?
;;   Are there better tools for trampolining requests?

;; The task protocol is that tasks are middleware functions
;; 
;;   [handler] -> [config stack request] -> resp
;;
;; "Handlers" should attempt to parse or otherwise recognize requests they can process, and produce a
;; Ring response structure. The body of the response should NOT be encoded by any handler - encoding
;; should be left to ring response middleware.
;;
;; If a handler does not recognize or fails to handle a request, it should defer to the rest of the
;; middleware stack - although it may restart the request using the entire stack if need be.
;;
;; The root of the middleware stack should be a handler which always responds with an error,
;; recording that everything else failed to handle that particular request.
;;
;; Every handler which provides a command is required to participate in the "meta" request if it
;; implements a task.  Handlers must respond to the "meta" request, by accumulating whatever
;; metadata may be available about that task into the response body. The keys :kat/request-name
;; and :kat/doc are expected in metadata responses. It should be possible to extract all the
;; available commands and their relevant documentation from these responses.

(defn root-handler
  "A handler which just reports that no handler was found, and provides a root for the middleware
  metadata response protocol."
  [config stack request]
  (cond (= "meta" (first request))
        (resp/response [])

        :else
        (-> (resp/response {:msg "No handler for request",
                            :request request})
            (resp/status 400))))

(defonce +request-counter+
  (atom 0))

(defn handle-stop-server
  {:kat/request-name "stop-server"
   :kat/doc "Shut down the server when outstanding requests complete."}
  [handler]
  (fn [config stack request]
    (case (first request)
      "meta"
      (update (handler config stack request)
              :body conj (meta #'handle-stop-server))

      "stop-server"
      (do ;; Use a future to schedule the shutdown
        (set-validator! +request-counter+
                        (fn [newval]
                          (when (= newval 0)
                            (future
                              (Thread/sleep 1)
                              (shutdown-agents)
                              (System/exit 1)))
                          true))
        ;; Return a response
        (-> (resp/response {:msg "Scheduling shutdown"})
            (resp/status 202)))

      (handler config stack request))))

(defn guard-stop-server
  "An implementation detail of stopping the server."
  [handler]
  (fn [config stack request]
    (swap! +request-counter+ inc)
    (try (handler config stack request)
         (finally
           (swap! +request-counter+ dec)))))

(defn handle-show-request
  {:kat/request-name "show-request"
   :kat/doc "Show the request as seen by the server (for debugging)"}
  [handler]
  (fn [config stack request]
    (case (first request)

      "meta"
      (update (handler config stack request)
              :body conj (meta #'handle-show-request))

      "show-request"
      (-> (resp/response {:config config
                          :request request})
          (resp/status 200))

      (handler config stack request))))

(defn handle-start-server
  {:kat/request-name "start-server"
   :kat/doc "A task which will cause the server to be started. Performs no other action."}
  [handler]
  (fn [config stack request]
    (case (first request)

      "meta"
      (update (handler stack config request) :body conj (meta #'handle-start-server))

      "start-server"
      (-> (resp/response {:msg "Started server!"})
          (resp/status 200))

      (handler stack config request))))

(defn wrap-help
  "Implement help by letting all the handlers report their help, then formatting it."
  {:kat/request-name "help",
   :kat/doc "Describe a command in detail, or sketch all commands"}
  [handler]
  (fn self [config stack request]
    (cond (contains? #{["-h"] ["--help"] ["help"] []} request)
          (update (stack config stack (cons "meta" (rest request)))
                  :body (fn [pairs]
                          {:msg (str "Katamari - roll up your software into artifacts!\n"
                                     "\n"
                                     "Usage:\n"
                                     "  ./kat [command] [flags] [targets]\n"
                                     "\n"
                                     "Commands:\n"
                                     (str/join "\n"
                                               (map (fn [{:keys [:kat/doc :kat/request-name]}]
                                                      (str "  " request-name " - " doc))
                                                    pairs)))}))

          (= "meta" (first request))
          (update (handler config stack request)
                  :body conj (meta #'wrap-help))

          :else
          (handler config stack request))))

(defn wrap-list
  "Implement list-commands by executing help and using only the task names."
  {:kat/request-name "list-commands",
   :kat/doc "Enumerate the available commands briefly"}
  
  [handler]
  (fn [config stack request]
    (case (first request)
      "list-commands"
      (update (stack config stack (cons "meta" (rest request)))
              :body (fn [pairs]
                      {:msg (str "Commands:\n"
                                 (str/join "\n" (map (comp #(str "  " %)
                                                           :kat/request-name)
                                                     pairs)))}))

      "meta"
      (update (handler config stack request)
              :body conj (meta #'wrap-list))

      (handler config stack request))))
