(ns katamari.web-server
  "A web server which makes it possible to boot Katamari once and amortize its startup cost across
  potentially many build or code evaluation requests."
  (:require [clojure.java.io :as jio]
            [clojure.java.classpath :as jcp]
            [compojure.core :refer [context defroutes GET PUT POST]]
            [cheshire.core :as json]
            [ring.util.response :as resp]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.session :as session]))

;;;; Config crap

(defn key-fn [k]
  (keyword (.replaceAll k "_" "-")))

;; FIXME (arrdem 2018-09-29):
;;   Migrate this ... somewhere or use a real parser.
;;   Turns out I already have instaparse as a transitive?
(defn parse-cfg [fname]
  (let [f ^java.io.File (jio/file fname)]
    (when (.exists f)
      (->> (java.io.FileReader. f)
           (java.io.BufferedReader.)
           line-seq
           (reduce (fn [acc l]
                     (if-let [m (re-find #"(?<name>[[\w_]&&[^=]]+)=(?<value>.+)$" l)]
                       (let [[_ k v] m]
                         ;; FIXME (arrdem 2018-09-29):
                         ;;   Lazy keywordization is lazy
                         (assoc acc (key-fn k) v))
                       acc))
                   {})))))

(defn json-response
  "Helper for returning JSON coded responses.

  JSON codes the given object in a single shot, returning a 200 response by default unless the user
  specifies an optional status code."
  ([obj]
   (json-response obj 200))
  ([obj code]
   (-> obj
       (json/encode)
       (resp/response)
       (resp/status code)
       (resp/update-header "content-type" (constantly "application/json")))))

;;;; Tasks

;; The task protocol is that tasks are middleware functions [handler] -> [config request] -> resp
;; 
;; "Handlers" should attempt to parse or otherwise recognize requests they can process, and produce a
;; Ring response structure. The body of the response should NOT be encoded by any handler - encoding
;; should be left to ring response middleware.
;;
;; If a handler does not recognize or fails to handle a request, it should defer to the rest of the
;; middleware stack.
;;
;; The root of the middleware stack should be a handler which always responds with an error,
;; recording that everything else failed to handle that particular request.
;;
;; Every handler which provides a command is required to participate in the "list-commands"
;; and "help" requests. The response from "list-commands" should be a sequence of pairs of the name
;; of the command(s) recognized by the handler stack, and a maximally terse description of the
;; command and its grammar. When handling this request 

(defonce +request-counter+
  (atom 0))

(defn handle-stop-server [handler]
  (fn [config request]
    (cond (= ["stop-server"] request)
          (do
            ;; Use a future to schedule the shutdown
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
          
          :else (handler config request))))

(defn guard-stop-server [handler]
  (fn [config request]
    (swap! +request-counter+ inc)
    (try (handler config request)
         (finally
           (swap! +request-counter+ dec)))))

(defn handle-classpath [hander]
  (fn [config request]
    ))

(def +request-middleware+
  (-> (fn [config request]
        (json-response {:msg "No handler for request",
                        :request request}
                       400))
      handle-stop-server
      guard-stop-server))

;;;; The server routes

(defroutes +api-v0+
  (context "/api/v0" []
    (GET "/ping" []
      (-> {"status" "ok"
           "ts" (System/currentTimeMillis)}
          (json-response 200)))

    (GET "/request" {:keys [body]}
      (let [{:keys [request config-file repo-root] :as json-body} (json/parse-string (slurp body) key-fn)
            config (-> (parse-cfg config-file)
                       (merge (dissoc json-body :request)))]
        (+request-middleware+ config request)))))

(defroutes +app+
  +api-v0+)

;; FIXME (arrdem 2018-09-29):
;;   Use component maybe?
(defonce +instance+
  (atom nil))

(defn start-web-server! [cfg]
  (let [jetty-cfg {:port (Long/parseLong (:server-port cfg "3636"))
                   :host (:server-addr cfg "127.0.0.1")
                   :join? false}
        jetty-inst (-> #'+app+
                       session/wrap-session
                       (run-jetty jetty-cfg))]
    (reset! +instance+ jetty-inst)))

(defn stop-web-server! []
  (swap! +instance+
         #(when-let [inst %]
            (.stop %)
            nil)))

;; FIXME (arrdem 2018-09-29):
;;   Should also embed an nREPL server and make that discoverable somehow.
(defn -main
  [config-file]
  (let [cfg (parse-cfg config-file)]
    (prn cfg)
    (start-web-server! cfg)))
