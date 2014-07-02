(ns clojush.pushgp.breed
  (:use [clojush globals random simplification individual]
        [clojush.pushgp parent-selection genetic-operators])
  (:require [clj-random.core :as random]))

; A map of genetic operator keywords to maps containing the genetic operator
; functions and number of parents
(def genetic-operators
  {:reproduction {:fn reproduction :parents 1}
   :alternation {:fn alternation :parents 2}
   :uniform-mutation {:fn uniform-mutation :parents 1}
   :uniform-close-mutation {:fn uniform-close-mutation :parents 1}
   :uniform-silent-mutation {:fn uniform-silent-mutation :parents 1}
   })

(defn revert-too-big-child
  "Determines what individual should replace a child program that exceeds the
   size limit. Options are:
     :parent -- one of the parents (default)
     :empty  -- an empty program
     :random -- a random program
   In future, may implement :delete, which deletes some of the instructions
   in a parent."
  [parent {:keys [replace-child-that-exceeds-size-limit-with atom-generators
                  max-points-in-initial-program]
           :as argmap}]
  (case replace-child-that-exceeds-size-limit-with
    :parent parent
    :empty (make-individual :genome '())
    :random (make-individual :genome (random-code max-points-in-initial-program atom-generators argmap))
    ))


(defn perform-genetic-operator-list
  "Recursively applies the genetic operators in operator-list, using
   first-parent as the first parent for each operator call, to create a new
   child."
  [operator-list first-parent population location argmap]
  (if (empty? operator-list)
    first-parent
    (let [operator (first operator-list)
          num-parents (:parents (get genetic-operators operator))
          parents (repeatedly (dec num-parents) #(select population location argmap))
          op-fn (:fn (get genetic-operators operator))]
      (recur (rest operator-list)
             (apply op-fn (concat (vector first-parent) parents (vector argmap)))
             population
             location
             argmap))))

(defn perform-genetic-operator
  "Takes a single genetic operator keyword or a sequence of operator keywords,
   and performs them to create a new individual. Uses recursive helper function
   even with a single operator by putting that operator in a vector."
  [operator population location {:keys [max-points] :as argmap}]
  (let [first-parent (select population location argmap)
        child (if (sequential? operator)
                (perform-genetic-operator-list operator first-parent population location argmap)
                (perform-genetic-operator-list (vector operator) first-parent population location argmap))]
    (if (> (count (:genome child)) max-points) ; Check if too big
      (revert-too-big-child first-parent argmap)
      (assoc child :parent first-parent))))

(defn breed
  "Returns an individual bred from the given population using the given parameters."
  [agt ;necessary since breed is called using swap! or send, even though not used
   location rand-gen population
   {:keys [genetic-operator-probabilities] :as argmap}]
  (random/with-rng rand-gen
    (let [prob (lrand)]
      (loop [vectored-go-probabilities (reductions #(assoc %2 1 (+ (second %1) (second %2)))
                                                   (vec genetic-operator-probabilities))]
        (if (<= prob (second (first vectored-go-probabilities)))
          (perform-genetic-operator (first (first vectored-go-probabilities)) population location argmap)
          (recur (rest vectored-go-probabilities)))))))
