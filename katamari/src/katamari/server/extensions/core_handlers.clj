(ns katamari.server.extensions.core-handlers
  "Core Katamari tasks.

  You could turn off or do without these, but it'd be weird."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.string :as str]
            [katamari.server.extensions :refer [defwrapper defhandler install-handler!]]
            [ring.util.response :as resp]))

(defonce +request-counter+
  (atom 0))

(defhandler stop-server
  "Shut down the server after all outstanding requests complete."
  [handler config stack request]
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
    (->  {:intent :msg
          :msg "Scheduling shutdown"}
         (resp/response)
         (resp/status 202))))

(defwrapper guard-stop-server
  "An implementation detail of stopping the server."
  [handler config stack request]
  (swap! +request-counter+ inc)
  (try (handler config stack request)
       (finally
         (swap! +request-counter+ dec))))

(defhandler restart-server
  "Reboot the server, reloading the config and other options."
  [handler config stack request]
  ;; FIXME (arrdem 2018-10-17):
  ;;   This isn't a great implementation, but it does work.
  ;;   Solves the race condition of shutdown while responding,
  ;;   Solves the issue of rebooting with any/all JVM options,
  ;;   Just a silly use of eval / a subshell somehow
  (-> {:intent :sh
       :sh "$KAT stop-server; $KAT start-server"}
      (resp/response)
      (resp/status 200)))

(defhandler show-request
  "Show the request and config context as seen by the server (for debugging)"
  [handler config stack request]
  (->  {:intent :json
        :config config
        :request request}
       (resp/response)
       (resp/status 200)))

(defhandler start-server
  "A task which will cause the server to be started.

Reports the ports on which the HTTP and nREPL servers are running."
  [handler config stack request]
  (-> (merge {:intent :msg
              :msg (format (str "Started server!\n"
                                "  http port: %s\n"
                                "  nrepl port: %s\n")
                           (:server-http-port config)
                           (:server-nrepl-port config))}
             (select-keys config [:server-http-port
                                  :server-nrepl-port]))
      (resp/response)
      (resp/status 200)))

;; Note that, as this task does some dancing around the ENTIRE request, it doesn't fit trivially
;; into the usual defhandler patter, which assumes that there's a clear singular task name to
;; dispatch with. So we have to `install-handler!` ourselves. Oh well.

(defn handle-help
  {:kat/task-name "help",
   :kat/doc "Describe a command in detail, or sketch all commands"}
  [handler]
  (fn [config stack request]
    (cond (contains? #{["-h"] ["--help"] ["help"] []} request)
          (update (stack config stack (cons "meta" (rest request)))
                  :body
                  (fn [{:keys [metadata]}]
                    {:intent :msg
                     :msg (->> metadata
                               (sort-by :kat/task-name)
                               (map (fn [{:keys [:kat/doc :kat/task-name]}]
                                      (str "  " task-name ":\n"
                                           (str/join "\n"
                                                     (map #(str "    " %)
                                                          (line-seq
                                                           (java.io.BufferedReader.
                                                            (java.io.StringReader.
                                                             doc))))))))
                               (str/join "\n\n")
                               (str "Katamari - roll up your software into artifacts!\n"
                                    "\n"
                                    "Usage:\n"
                                    "  ./kat [-r|-j|-m] [command] [flags] [targets]\n"
                                    "\n"
                                    "Flags:\n"
                                    "  -r, --raw  - print the raw JSON of Katamari server responses\n"
                                    "  -j, --json - format the JSON of Katamari server responses\n"
                                    "  -m, --message - print only the message part of server responses\n"
                                    "\n"
                                    "Commands:\n"))
                     :metadata metadata}))

          (and (= "help" (first request))
               (second request))
          (-> (stack config stack (cons "meta" (rest request)))
              (update :body (fn [{:keys [metadata]}]
                              {:intent :msg
                               :msg (-> (filter (fn [{:keys [:kat/task-name]}]
                                                  (= task-name (second request)))
                                                metadata)
                                        first
                                        (or {:kat/doc (str "No such command - "
                                                           (pr-str (second request)))})
                                        :kat/doc)})))

          (some #{"-h" "--help"} request)
          (recur config stack (cons "help" (remove #{"-h" "--help"} request)))

          (= "meta" (first request))
          (update-in (handler config stack request)
                     [:body :metadata] conj (meta #'handle-help))

          :else
          (handler config stack request))))

(install-handler! #'handle-help)

(defhandler list-tasks
  "Enumerate the available tasks.

Do not report their help information."
  
  [handler config stack request]
  (update (stack config stack (cons "meta" (rest request)))
          :body
          (fn [{:keys [metadata]}]
            (let [task-names (sort (mapv :kat/task-name metadata))]
              {:intent :msg
               :msg (str "Commands:\n"
                         (str/join "\n" (map #(str "  " %) task-names)))
               :tasks task-names}))))
