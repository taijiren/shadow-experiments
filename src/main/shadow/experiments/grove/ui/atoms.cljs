(ns shadow.experiments.grove.ui.atoms
  (:require
    [shadow.experiments.grove.components :as comp]
    [shadow.experiments.grove.protocols :as gp]
    [shadow.experiments.grove.runtime :as rt]
    [shadow.experiments.grove.ui.util :as util]))

(util/assert-not-in-worker!)

(deftype EnvWatch [key-to-atom path default the-atom ^:mutable val component idx]
  gp/IBuildHook
  (hook-build [this c i]
    (let [atom (-> (get (comp/get-env c) ::rt/runtime-ref)
                   deref
                   (get key-to-atom))]
      (when-not atom
        (throw (ex-info "no atom found under key" {:key key-to-atom :path path})))
      (EnvWatch. key-to-atom path default atom nil c i)))

  gp/IHook
  (hook-init! [this]
    (set! val (get-in @the-atom path default))
    (add-watch the-atom this
      (fn [_ _ _ new-value]
        ;; check immediately and only invalidate if actually changed
        ;; avoids kicking off too much work
        (let [next-val (get-in new-value path default)]
          (when (not= val next-val)
            (set! val next-val)
            (comp/hook-invalidate! component idx))))))

  (hook-ready? [this] true) ;; born ready
  (hook-value [this] val)
  (hook-update! [this]
    ;; only gets here if val actually changed
    true)

  (hook-deps-update! [this new-val]
    (throw (ex-info "shouldn't have changing deps?" {})))
  (hook-destroy! [this]
    (remove-watch the-atom this)))

(deftype AtomWatch [the-atom access-fn ^:mutable val component idx]
  gp/IBuildHook
  (hook-build [this c i]
    (AtomWatch. the-atom access-fn nil c i))

  gp/IHook
  (hook-init! [this]
    (set! val (access-fn nil @the-atom))
    (add-watch the-atom this
      (fn [_ _ old new]
        ;; check immediately and only invalidate if actually changed
        ;; avoids kicking off too much work
        ;; FIXME: maybe shouldn't check equiv? only identical?
        ;; pretty likely that something changed after all
        (let [next-val (access-fn old new)]
          (when (not= val next-val)
            (set! val next-val)
            (comp/hook-invalidate! component idx))))))

  (hook-ready? [this] true) ;; born ready
  (hook-value [this] val)
  (hook-update! [this]
    ;; only gets here if value changed
    true)
  (hook-deps-update! [this new-val]
    ;; FIXME: its ok to change the access-fn
    (throw (ex-info "shouldn't have changing deps?" {})))
  (hook-destroy! [this]
    (remove-watch the-atom this)))
