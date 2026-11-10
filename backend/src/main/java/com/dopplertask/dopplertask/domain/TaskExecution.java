package com.dopplertask.dopplertask.domain;

import com.dopplertask.dopplertask.service.WebhookReply;

import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.LoopState;
import com.dopplertask.dopplertask.domain.action.integration.JsonApi;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "TaskExecution")
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "execution_parameter_mapping", joinColumns = @JoinColumn(name = "execution_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "parameter_id", referencedColumnName = "id"))
    @MapKey(name = "paramName")
    private Map<String, ExecutionParameter> parameters = new HashMap<>();

    @ManyToOne
    @JoinColumn
    private Task task;

    @OneToMany(mappedBy = "taskExecution", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<TaskExecutionLog> logs = new ArrayList<>();

    private Date startdate;
    private Date enddate;

    // Useful for linked tasks to know how deep in the linking it has reached to avoid stack overflow.
    private Integer depth = 0;

    @Enumerated(EnumType.STRING)
    private TaskExecutionStatus status = TaskExecutionStatus.CREATED;

    @Transient
    private Action currentAction;

    // Part of the record of a run, next to what it did.
    @Column(columnDefinition = "boolean default false")
    private boolean startedByAgent = false;

    // The step a resumed run starts at, named by its first port as in TaskExecutionStep.
    @Column
    private String resumePortId;

    // The one-time key that continues a waiting run.
    @Column(unique = true)
    private String waitKey;

    // The run this run continues, so that a person who stops it before it starts also stops what it would continue.
    @Column
    private Long resumedFrom;

    // Whether this run goes on after a person answered a waiting run, so that a resume keeps the values they saw.
    @Column(columnDefinition = "boolean default false")
    private boolean afterAnswer = false;

    // What arrived at the address of a waiting run, kept until the run that continues it hands it to that step.
    @Column(columnDefinition = "TEXT")
    private String resumeEvent;

    @Transient
    private Map<Long, Integer> actionAccessCountMap = new HashMap<>();

    private boolean success = true;

    @OneToMany(mappedBy = "taskExecution", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<TaskExecutionStep> steps = new ArrayList<>();

    // Why the run failed, for the caller waiting on it. The logs hold it permanently.
    @Transient
    private String failureReason;

    // The caller of a webhook who waits for an answer, when the trigger lets the task decide it.
    @Transient
    private WebhookReply webhookReply;

    // The loops that are running, the innermost last, by the port id of their step.
    @Transient
    private LinkedHashMap<String, LoopState> loops = new LinkedHashMap<>();

    // The loops as JSON when the run ended, so that a run resumed inside a pass carries on with the rest of the items.
    @Lob
    @Column(columnDefinition = "TEXT")
    private String runningLoops;

    // What each step gave so far, by the name of the step, for the steps after it. The latest wins when a name repeats.
    @Transient
    private Map<String, StepOutput> stepOutputs = new LinkedHashMap<>();

    // Values the step that runs now asked for and nothing gave. They go to the record of that step.
    @Transient
    private Set<String> missingValues = new LinkedHashSet<>();

    // Set when the run is a preview, as JSON, since a run starts on another thread than the one that asked for it.
    @Lob
    @Column(columnDefinition = "TEXT")
    private String preview;

    // How many more visits to a step a run may make, since every item of a loop visits the steps of a pass again.
    @Transient
    private long loopVisits = 0;

    public Map<String, LoopState> getLoops() {
        return loops;
    }

    public void startLoop(LoopState state) {
        loops.put(state.getStep(), state);
        loopVisits += state.getCount();
    }

    public void endLoop(String step) {
        loops.remove(step);
    }

    /** Back at a loop, so the loops that started inside its pass are done. */
    public void returnToLoop(String step) {
        boolean inside = false;
        for (Iterator<String> running = loops.keySet().iterator(); running.hasNext(); ) {
            String next = running.next();
            if (inside) {
                running.remove();
            }
            inside |= next.equals(step);
        }
    }

    /** The loop the run is inside, where a pass that ends goes back to. */
    public LoopState getInnermostLoop() {
        return loops.isEmpty() ? null : loops.lastEntry().getValue();
    }

    public long getLoopVisits() {
        return loopVisits;
    }

    /** Keeps the loops that are still running with the run, for a resume. */
    public void keepRunningLoops() {
        runningLoops = loops.isEmpty() ? null : JsonApi.INSTANCE.getMapper().writeValueAsString(loops.values());
    }

    /** Takes over the loops an earlier run was inside when it failed. They are stored, since the run may start on another thread. */
    public void continueLoopsOf(TaskExecution earlierRun) {
        runningLoops = earlierRun.runningLoops;
    }

    /** Picks up the stored loops when the run starts, so that a resumed run goes on with the rest of their items. */
    public void restoreRunningLoops() {
        if (runningLoops != null && loops.isEmpty()) {
            List.of(JsonApi.INSTANCE.getMapper().readValue(runningLoops, LoopState[].class)).forEach(this::startLoop);
        }
    }

    public Map<String, StepOutput> getStepOutputs() {
        return stepOutputs;
    }

    public void noteMissingValue(String note) {
        missingValues.add(note);
    }

    /** Hands over what the current step missed, so that the next step starts with none. */
    public List<String> takeMissingValues() {
        List<String> taken = List.copyOf(missingValues);
        missingValues.clear();
        return taken;
    }

    public Preview getPreview() {
        return preview == null ? null : JsonApi.INSTANCE.getMapper().readValue(preview, Preview.class);
    }

    public void setPreview(Preview preview) {
        this.preview = preview == null ? null : JsonApi.INSTANCE.getMapper().writeValueAsString(preview);
    }

    /** A run that continues a preview is one too, since the steps before it gave values that were never sent. */
    public void continuePreviewOf(TaskExecution earlierRun) {
        if (preview == null) {
            preview = earlierRun.preview;
        }
    }

    /** Whether a preview keeps this step from sending. */
    public boolean holds(Action step) {
        Preview running = getPreview();
        return running != null && !running.getSentForReal().contains(step.getPortId());
    }

    public WebhookReply getWebhookReply() {
        return webhookReply;
    }

    public void setWebhookReply(WebhookReply webhookReply) {
        this.webhookReply = webhookReply;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Map<String, ExecutionParameter> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, ExecutionParameter> parameters) {
        this.parameters = parameters;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public Date getStartdate() {
        return startdate;
    }

    public void setStartdate(Date startdate) {
        this.startdate = startdate;
    }

    public Date getEnddate() {
        return enddate;
    }

    public void setEnddate(Date enddate) {
        this.enddate = enddate;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<TaskExecutionStep> getSteps() {
        return steps;
    }

    public void addStep(TaskExecutionStep step) {
        steps.add(step);
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void addLog(TaskExecutionLog executionLog) {
        logs.add(executionLog);
    }

    public List<TaskExecutionLog> getLogs() {
        return logs;
    }

    public TaskExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(TaskExecutionStatus status) {
        this.status = status;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public boolean isStartedByAgent() {
        return startedByAgent;
    }

    public void setStartedByAgent(boolean startedByAgent) {
        this.startedByAgent = startedByAgent;
    }

    public String getResumePortId() {
        return resumePortId;
    }

    public void setResumePortId(String resumePortId) {
        this.resumePortId = resumePortId;
    }

    public String getWaitKey() {
        return waitKey;
    }

    public void setWaitKey(String waitKey) {
        this.waitKey = waitKey;
    }

    public Long getResumedFrom() {
        return resumedFrom;
    }

    public void setResumedFrom(Long resumedFrom) {
        this.resumedFrom = resumedFrom;
    }

    /** A person stops a waiting run: its address answers nothing more, and an answer it got is dropped. */
    public void stopWaiting(String reason) {
        waitKey = null;
        resumeEvent = null;
        abort(reason);
    }

    public boolean isAfterAnswer() {
        return afterAnswer;
    }

    public void setAfterAnswer(boolean afterAnswer) {
        this.afterAnswer = afterAnswer;
    }

    /** Whether the address of this waiting run was called, so that a run may continue it with what arrived. */
    public boolean isAnswered() {
        return status == TaskExecutionStatus.WAITING && waitKey == null && resumeEvent != null;
    }

    public String getResumeEvent() {
        return resumeEvent;
    }

    public void setResumeEvent(String resumeEvent) {
        this.resumeEvent = resumeEvent;
    }

    public Action getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(Action currentAction) {
        this.currentAction = currentAction;
    }

    public int getActionAccessCount(Long actionId) {
        Integer count = this.actionAccessCountMap.get(actionId);
        if (count == null) {
            return 0;
        } else {
            return count;
        }
    }

    /**
     * Add access count to an action.
     *
     * @param actionId
     */
    public void addActionAccessCountByOne(Long actionId) {
        Integer count = this.actionAccessCountMap.get(actionId);
        if (count == null) {
            this.actionAccessCountMap.put(actionId, 1);
        } else {
            this.actionAccessCountMap.put(actionId, count + 1);
        }
    }

    /** Ends the run as failed, and says why both in its log and as its reason. */
    public void fail(String reason) {
        end(TaskExecutionStatus.FAILED, reason);
    }

    /** A person stopped the run. */
    public void abort(String reason) {
        end(TaskExecutionStatus.ABORTED, reason);
    }

    private void end(TaskExecutionStatus status, String reason) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setTaskExecution(this);
        log.setOutput(reason);
        log.setDate(new Date());
        log.setBroadcasted(true);
        addLog(log);
        setStatus(status);
        setSuccess(false);
        setFailureReason(reason);
        setEnddate(new Date());
    }
}
