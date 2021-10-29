(ns pedestal-play.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]
            [io.pedestal.log :as log]
            [io.pedestal.http.sse :as sse]
            [clojure.core.async :as async]))

(defn about-page
  [request]
  (log/counter ::about-hits 1)
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))
(defn home-page
  [request]
  (log/counter ::home-hits 1)
  (ring-resp/response "Hello World!"))

;;test-doksanbir
(defn doksanbir-page
  [request]
  (log/counter ::doksanbir-hits 1)
  (ring-resp/response "Alp Demir Doksanbir"))

;;curl -XPOST -d "formparam=1" "http://localhost:8080/debug/1?qparam=1"
(defn debug-page
  [request]
  (log/counter ::debug-hits 1)
  (ring-resp/response
    (cheshire.core/generate-string
      (select-keys request
                   [:params :path-params :query-params :form-params]))))

;; Handler for /hello route
(defn hello-page
  [request]
  (log/counter ::hello-hits 1)
  (ring-resp/response
    (let [resp (clojure.string/trim (get-in request [:query-params :name]))]
      (if (empty? resp) "Hello World!" (str "Hello " resp "!")))))

(def msg-play
  {:name ::msg-play
   :enter
         (fn [context]
           (update-in context [:request :query-params :name] clojure.string/upper-case))
   :leave
         (fn [context] (update-in context [:response :body]
                                  #(str % "Good to see you!")))
   :error (fn [context ex-info]
            (assoc context :response  {:status 400 :body "Invalid request/name!"}))})

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])


(defn sse-stream-ready
  "Starts sending counter events to client."
  [event-ch context]
  (let [count-num (Integer/parseInt
                    (or (-> (context :request)
                            :query-params :counter) "5"))]
    (loop [counter count-num]
      (async/put!
        event-ch {:name "count"
                  :data (str counter ", T: "
                             (.getId (Thread/currentThread)))})
      (Thread/sleep 2000)
      (if (> counter 1)
        (recur (dec counter))
        (do
          (async/put! event-ch {:name "close" :data "I am done!"})
          (async/close! event-ch))))))

;; Tabular routes
; (def routes #{["/" :get (conj common-interceptors `home-page)]
;            ["/about" :get (conj common-interceptors `about-page)]})

(def routes #{{:app-name "Pedestal Play" :host "localhost" :port 8080 :scheme :http}
              ["/" :get (conj common-interceptors `home-page)]
              ["/about" :get (conj common-interceptors `about-page)]
              ["/doksanbir" :get (conj common-interceptors `doksanbir-page)]
              ["/debug/:id" :post (conj common-interceptors `debug-page)]
              ["/hello" :get (conj common-interceptors `msg-play `hello-page)]
              ["/events" :get
               [(sse/start-event-stream sse-stream-ready)]]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
;(def routes
;  `[[["/" {:get home-page}
;      ^:interceptors [(body-params/body-params) http/html-body]
;      ["/about" {:get about-page}]]]])


;; Consumed by pedestal-play.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env                     :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes            routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Tune the Secure Headers
              ;; and specifically the Content Security Policy appropriate to your service/application
              ;; For more information, see: https://content-security-policy.com/
              ;;   See also: https://github.com/pedestal/pedestal/issues/499
              ;;::http/secure-headers {:content-security-policy-settings {:object-src "'none'"
              ;;                                                          :script-src "'unsafe-inline' 'unsafe-eval' 'strict-dynamic' https: http:"
              ;;                                                          :frame-ancestors "'none'"}}

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path     "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ;;  This can also be your own chain provider/server-fn -- http://pedestal.io/reference/architecture-overview#_chain_provider
              ::http/type              :immutant
              ;;::http/host "localhost"
              ::http/port              8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2?  false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false
                                        ;; Alternatively, You can specify you're own Jetty HTTPConfiguration
                                        ;; via the `:io.pedestal.http.jetty/http-configuration` container option.
                                        ;:io.pedestal.http.jetty/http-configuration (org.eclipse.jetty.server.HttpConfiguration.)
                                        }})
