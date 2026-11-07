package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.action.Action;

import java.net.URI;

/**
 * What steps may do. An install trusts its own tasks, so by default nothing is refused. An add-on can tighten this when
 * tasks come from people who must not reach the machine or each other.
 */
public interface StepRules {
    /** Whether this kind of step is offered at all. */
    default boolean offers(Class<? extends Action> kind) {
        return true;
    }

    /** Why this configured step may not run, or null when it may. */
    default String refusalOf(Action step) {
        return null;
    }

    /** Why a step may not call this address, or null when it may. */
    default String refusalOfAddress(URI address) {
        return null;
    }

    /** Whether scripts get plain values only, with no way into Java or the machine. */
    default boolean scriptsAreRestricted() {
        return false;
    }

    static StepRules active() {
        return Holder.active;
    }

    static void use(StepRules rules) {
        Holder.active = rules;
    }

    final class Holder {
        private static volatile StepRules active = new StepRules() { };

        private Holder() {
        }
    }
}
