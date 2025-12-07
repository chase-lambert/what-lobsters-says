(ns lobsters-ext.core
  (:require [lobsters-ext.api :as api]
            [cljs.core.async :refer [go <!]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as str]))

(defn get-element [id]
  (js/document.getElementById id))

(defn set-text! [id text]
  (when-let [el (get-element id)]
    (set! (.-textContent el) text)))

(defn set-html! [id html]
  (when-let [el (get-element id)]
    (set! (.-innerHTML el) html)))

(defn escape-html [s]
  (-> (str s)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#39;")))

(defn get-current-tab []
  (js/Promise.
   (fn [resolve reject]
     (.. js/chrome -tabs
         (query #js {:active true :currentWindow true}
                (fn [tabs]
                  (if js/chrome.runtime.lastError
                    (reject js/chrome.runtime.lastError)
                    (if-let [tab (aget tabs 0)]
                      (resolve tab)
                      (reject (js/Error. "No active tab"))))))))))

(defn open-tab [url]
  (.. js/chrome -tabs (create #js {:url url})))

(defn render-story [story clean-target-url]
  (let [clean-story-url (api/clean-url (:url story))
        is-related      (not= (str/replace clean-story-url #"/+$" "")
                              (str/replace clean-target-url #"/+$" ""))
        related-html    (if is-related
                          (str "<div class=\"url-label\" style=\"margin-top:4px;\">For related url: "
                               (escape-html clean-story-url) "</div>")
                          "")]
    (str "<li class=\"story\" data-link=\"https://lobste.rs/s/" (escape-html (:short_id story)) "\">"
         "<div class=\"story-title\">" (escape-html (:title story)) "</div>"
         "<div class=\"story-meta\">"
         "<span class=\"highlight\">" (escape-html (:score story)) "</span> points • "
         "<span class=\"highlight\">" (escape-html (or (:comment_count story) 0)) "</span> comments • "
         (escape-html (api/time-since (:created_at story)))
         "</div>"
         related-html
         "</li>")))

(defn render-results [stories current-url clean-url]
  (if (empty? stories)
    ;; No results
    (let [search-url (str "https://lobste.rs/search?q="
                          (js/encodeURIComponent current-url)
                          "&what=stories")]
      (set-html! "content"
                 (str "<li class=\"message\">"
                      "<p>No recent stories found for this URL.</p>"
                      "<p style=\"font-size:12px;color:#666;margin-top:8px;\">"
                      "Searching hottest/newest/active stories only. "
                      "Older stories may exist."
                      "</p>"
                      "<div style=\"display:flex;flex-direction:column;gap:8px;margin-top:12px;\">"
                      "<button class=\"btn\" data-link=\"" (escape-html search-url) "\">"
                      "Search All Lobsters Stories"
                      "</button>"
                      "<button class=\"btn\" data-link=\"https://lobste.rs/stories/new?url="
                      (js/encodeURIComponent clean-url)
                      "\">"
                      "Submit to Lobsters"
                      "</button>"
                      "</div>"
                      "</li>")))
    ;; Show results
    (let [max-display (min (count stories) 4)
          stories-to-show (take max-display stories)
          html (str/join ""
                         (concat
                          (map #(render-story % clean-url) stories-to-show)
                          (when (> (count stories) 4)
                            [(str "<li class=\"message\" style=\"font-size:12px;color:#666;\">"
                                  "Showing 4 of " (count stories) " matching stories"
                                  "</li>")])))]
      (set-html! "content" html)))

  (when-let [root (get-element "content")]
    (doseq [link (.querySelectorAll root "[data-link]")]
      (.addEventListener link "click"
                         (fn [e]
                           (.preventDefault e)
                           (open-tab (.getAttribute link "data-link")))))))

(defn render-error [error]
  (set-html! "content"
             (str "<li class=\"message error\">"
                  "<p>Error loading stories:</p>"
                  "<pre>" (escape-html error) "</pre>"
                  "</li>")))

(defn render-loading []
  (set-html! "content" "<li class=\"loading\">Loading...</li>"))

(defn render-invalid-url [url]
  (set-html! "content"
             (str "<li class=\"message\">"
                  "<p>Not a valid URL:</p>"
                  "<pre>" (escape-html url) "</pre>"
                  "</li>")))

(defn process-current-page []
  (go
    (try
      (let [tab (<p! (get-current-tab))
            current-url (.-url tab)]
        (if-not (re-matches #"^https?://.+$" current-url)
          (render-invalid-url current-url)
          (let [clean-url (api/clean-url current-url)]
            (set-text! "url-label" clean-url)
            (when-let [label (get-element "url-label")]
              (set! (.-title label) clean-url))
            (render-loading)
            (let [domain (api/extract-domain current-url)
                  domain-result (when domain (<! (api/fetch-stories-by-domain domain)))
                  domain-matches (when (and domain-result (:success domain-result))
                                   (api/search-stories (:stories domain-result) current-url))]
              (if (seq domain-matches)
                (render-results domain-matches current-url clean-url)
                (let [result (<! (api/fetch-lobsters-stories))]
                  (if (:success result)
                    (let [stories (:stories result)
                          matches (api/search-stories stories current-url)]
                      (render-results matches current-url clean-url))
                    (render-error (:error result)))))))))
      (catch js/Error e
        (render-error (.-message e))))))

(defn display-version []
  (when-let [manifest (.. js/chrome -runtime (getManifest))]
    (when-let [el (get-element "version")]
      (set! (.-textContent el) (str "v" (.-version manifest))))))

(defn init []
  (display-version)
  (process-current-page))

(defn reload! []
  (js/console.log "Reloaded!"))
