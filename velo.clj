;; https://developer.broadcom.com/xapis/velocloud-orchestrator-api/latest/
;; cookie based authentication is commented out
;; token based authentication is in use

;; dump velo cloud edge hostname, interface name, interface IP and prefix length

(ns velo
  (:require [clj-http.client :as http]
            [cheshire.core :as cc]
            [clojure.string :as s]))

(comment
  "username password authentication"
  (def cs (clj-http.cookies/cookie-store))

  (defn api-call [url params]
    (let [params {:form-params params, :content-type :json, :cookie-store cs}
          rep (http/post (str "https://vco124-usca1.velocloud.net/portal/rest" url) params)]
      (when (= 200 (:status rep))
        (:body rep))))

  ;; login
  (api-call "/login/enterpriseLogin" {:username "x@x.com" , :password "x"}))

(def api-token "Token <x>")

(defn api-call [url params]
  (let [params {:form-params params, :content-type :json, :headers {:Authorization api-token}}
        resp (http/post (str "https://vco124-usca1.velocloud.net/portal/rest" url) params)]
    (when (= 200 (:status resp))
      (:body resp))))

;; get id-hostname map
(def m-id-hostname
  (let [t (api-call "/enterprise/getEnterpriseEdgeList" {:enterpriseId 189, :limit 0})]
    (->> (for [x (get (cc/parse-string t) "data")]
           [(get x "id") (s/replace (get x "name") " " "")])
         (into {}))))

;; get-interface-ip
;; return a list of [hostname, intfname, ip, prefix-len]
(defn get-interface-ip [[id hostname]]
  (let [s (api-call "/edge/getEdgeConfigurationStack" {:enterpriseId 189, :edgeId id})
        t (cc/parse-string s)
        ints (for [x (->> (mapcat #(get % "modules") t)
                          (mapcat #(get-in % ["data" "routedInterfaces"])))
                   :let [name       (get x "name")
                         addressing (get x "addressing")
                         cidrIP     (get addressing "cidrIp")
                         cidrPrefix (get addressing "cidrPrefix")]
                   :when cidrIP]
               [hostname name cidrIP cidrPrefix])
        subints (for [x (->> (mapcat #(get % "modules") t)
                             (mapcat #(get-in % ["data" "routedInterfaces"])))
                      subint (get x "subinterfaces")
                      :let [name       (get x "name")
                            subid      (get subint "subinterfaceId")
                            addressing (get subint "addressing")
                            cidrIP     (get addressing "cidrIp")
                            cidrPrefix (get addressing "cidrPrefix")]]
                  [hostname (str name "." subid) cidrIP cidrPrefix])]
    (concat ints subints)))

(def host-intf-ip-len (mapcat get-interface-ip m-id-hostname))

(def sorted-host-entries (->> (for [[hostname intf ip len] host-intf-ip-len
                                    :let [t (str hostname "_" intf)]]
                                [t (format "%-15s Velo_%-85s #%s/%s" ip t ip len)])
                              (sort-by first)
                              (map second)))

(doall (map println sorted-host-entries))
