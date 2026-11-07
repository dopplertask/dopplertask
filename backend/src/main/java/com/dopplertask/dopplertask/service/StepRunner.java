package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;

import java.util.Map;

/**
 * Runs what a task's author wrote somewhere other than this process. An install has none and runs everything itself.
 */
public interface StepRunner {
    /** Whether this step leaves the process. Steps that move through the task itself have to stay. */
    boolean runsElsewhere(Action step);

    /** Runs the step with its credential values, and brings back any changes it made to the parameters. */
    ActionResult runStep(Action step, Map<String, String> credentialValues, TaskExecution execution);

    /** Fills in a template or runs a script, as a step's own field would. */
    String evaluate(String field, TaskExecution execution, ActionResult result, ScriptLanguage language);

    static StepRunner active() {
        return Holder.active;
    }

    static void use(StepRunner runner) {
        Holder.active = runner;
    }

    final class Holder {
        private static volatile StepRunner active;

        private Holder() {
        }
    }
}
