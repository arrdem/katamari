(ns katamari.server.tasks
  "Helpers for defining Katamari task middlewares."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
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

(defn root-task-handler
  "A handler which just reports that no handler was found, and provides a root for the middleware
  metadata response protocol."
  [config stack request]
  (cond (= "meta" (first request))
        (resp/response [])

        :else
        (-> (resp/response {:msg "No handler for request",
                            :request request})
            (resp/status 400))))
