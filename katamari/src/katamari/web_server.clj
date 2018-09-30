(ns katamari.web-server
  "A web server which makes it possible to boot Katamari once and amortize its startup cost across
  potentially many build or code evaluation requests."
  (:require [clojure.java.io :as jio]
            [clojure.java.classpath :as jcp]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [context defroutes GET PUT POST]]
            [cheshire.core :as json]
            [ring.util.response :as resp]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.session :as session]
            [katamari.conf :as conf]
            [katamari.tasks :refer [root-task-handler]]
            [katamari.tasks.core :as t.c]))

;;;; Config crap

(defn key-fn [k]
  (keyword (.replaceAll k "_" "-")))

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

(def +request-middleware+
  (-> root-task-handler

      ;; Simple request handlers
      t.c/handle-start-server
      t.c/handle-show-request
      t.c/handle-stop-server

      ;; Handlers that hack the request
      t.c/wrap-list
      t.c/wrap-help

      ;; Pure inits that happen first
      t.c/guard-stop-server))

;;;; The server routes

(defroutes +api-v0+
  (context "/api/v0" []
    (GET "/ping" []
      (-> {"status" "ok"
           "ts"     (System/currentTimeMillis)}
          (json-response 200)))

    (GET "/request" {:keys [body]}
      (let [{:keys [request config-file repo-root]
             :as   json-body} (json/parse-string (slurp body) key-fn)
            config          (-> (conf/load config-file)
                                (merge (dissoc json-body :request)))]
        ;; Note that this enables the middleware stack to recurse
        (+request-middleware+ config +request-middleware+ request)))))

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
                       wrap-json-response
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
  (log/info "Loading config file" config-file)
  (let [cfg (conf/load config-file key-fn)]
    (log/info "Loaded config" cfg)
    (start-web-server! cfg)))

(comment
  (start-web-server!
   (conf/load "../kat.conf")))
