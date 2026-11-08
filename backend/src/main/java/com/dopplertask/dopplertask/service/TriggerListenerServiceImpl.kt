package com.dopplertask.dopplertask.service

import com.dopplertask.dopplertask.dao.TaskDao
import com.dopplertask.dopplertask.domain.Task
import com.dopplertask.dopplertask.domain.TaskExecutionStatus
import com.dopplertask.dopplertask.domain.action.trigger.Trigger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import com.dopplertask.dopplertask.domain.action.connector.ConnectorTrigger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import jakarta.annotation.PostConstruct


private const val RETRY_AFTER_FAILURE_MS = 5000L

@Service
@Transactional
open class TriggerListenerServiceImpl {

    private val logger = LoggerFactory.getLogger(TriggerListenerServiceImpl::class.java)
    private val threadPoolMap: MutableMap<String, ExecutorService>

    private val taskDao: TaskDao
    private val taskService: TaskService

    private val tasks: MutableSet<Task> = mutableSetOf()

    // The triggers each pool runs, so that they can let go of what they hold when their task stops.
    private val running: MutableMap<String, List<Trigger>> = java.util.concurrent.ConcurrentHashMap()


    @Autowired
    @Qualifier("transactionManager")
    protected lateinit var txManager: PlatformTransactionManager

    @Autowired
    protected lateinit var customerScope: ObjectProvider<CustomerScope>

    @Autowired
    protected lateinit var credentials: CredentialService

    @Autowired
    protected lateinit var database: JdbcTemplate

    @Autowired
    protected lateinit var runQueue: ObjectProvider<RunQueue>

    @Autowired
    protected lateinit var executionService: ExecutionService

    private val lastQueuedRun: MutableMap<String, Long> = ConcurrentHashMap()

    @Autowired
    @Lazy
    constructor(taskDao: TaskDao, taskService: TaskService) {
        this.taskDao = taskDao
        this.taskService = taskService
        this.threadPoolMap = mutableMapOf()

    }

    @PostConstruct
    fun init() {
        val tmpl = TransactionTemplate(txManager)
        customers().forEachCustomer { tmpl.executeWithoutResult { startActiveTriggers() } }

    }

    open fun startActiveTriggers() {
        var tasks = taskDao.findAllByActive(true)
        logger.info("Starting triggers for all tasks")
        tasks.removeIf { !it.isActive }
        tasks.sortByDescending {
            it.created
        }

        // Filter out tasks to only contain the latest
        var triggerTasks = mutableSetOf<Task>()
        triggerTasks.addAll(tasks)

        triggerTasks.forEach { task ->
            // One left from before, such as when stopping it failed, ends rather than running on unseen.
            threadPoolMap.put(poolName(task.name), Executors.newVirtualThreadPerTaskExecutor())?.shutdownNow()

            startTriggers(task)
        }

    }

    open fun updateTriggers(id: Long) {
        val task = taskDao.findById(id);
        if (task.isPresent) {
            val currentTask = task.get();

            stopTriggers(currentTask.name)

            if (currentTask.isActive) {
                threadPoolMap[poolName(currentTask.name)] = Executors.newVirtualThreadPerTaskExecutor()

                startTriggers(currentTask)
            }
        }

    }

    open fun stopTriggers(taskName: String) {
        threadPoolMap.remove(poolName(taskName))?.shutdownNow()
        running.remove(poolName(taskName))?.forEach { trigger ->
            try {
                signIn(trigger)
                trigger.stop()
            } catch (e: Exception) {
                logger.warn("A trigger of task $taskName could not let go of what it held. ${e.message}")
            }
        }
    }

    private fun signIn(trigger: Trigger) {
        trigger.credential?.takeIf { it.isNotBlank() }?.let { trigger.useCredentialValues(credentials.valuesFor(it, trigger.credentialType)) }
    }

    /** Every trigger of the current scope, such as when the scope goes away. */
    open fun stopAllTriggers() {
        val prefix = customers().current() + "/"
        threadPoolMap.keys.filter { it.startsWith(prefix) }.forEach { threadPoolMap.remove(it)?.shutdownNow() }
    }

    /** Every active trigger of the current scope, such as when the scope comes back. */
    open fun startAllTriggers() {
        TransactionTemplate(txManager).executeWithoutResult { startActiveTriggers() }
    }

    private fun customers(): CustomerScope = if (::customerScope.isInitialized) customerScope.getIfAvailable { LOCAL_CUSTOMER } else LOCAL_CUSTOMER

    private fun poolName(taskName: String?) = customers().current() + "/" + taskName

    private fun fire(trigger: Trigger) {
        logger.info("Running trigger")
        // A trigger that asks a service signs in as its credential, and remembers where it left off across restarts.
        signIn(trigger)
        if (trigger is ConnectorTrigger) {
            trigger.keepPlace = { database.update("UPDATE connector_trigger SET poll_state = ?, hook_key = ?, memory = ? WHERE id = ?", trigger.pollState, trigger.hookKey, trigger.memory, trigger.id) }
        }
        val triggerResult = trigger.trigger()

        val taskRequest = TaskRequest()
        taskRequest.triggerInfo = TriggerInfo(trigger.stepTypeName, trigger.triggerSuffix, triggerResult.resultMap).also { it.files = triggerResult.files }
        taskRequest.taskName = trigger.task?.name
        taskRequest.parameters = mutableMapOf()

        // Without a queue the run goes here, so the next one waits for it. A queue that an add-on supplies decides when
        // runs go and how many each customer has, so a schedule goes there too, and skips its turn while its last run
        // still waits or goes, as it would here.
        if (!::runQueue.isInitialized || runQueue.getIfAvailable() == null) {
            taskService.runRequest(taskRequest)
            return
        }
        val queue = runQueue.getObject()
        val key = poolName(trigger.task?.name) + "/" + trigger.javaClass.simpleName + "/" + trigger.triggerSuffix
        val last = lastQueuedRun[key]
        // Only what the queue still holds counts as waiting, so that a run left behind cannot hold the schedule for good.
        if (last != null && executionService.getExecution(last).map { it.status == TaskExecutionStatus.STARTED || it.status == TaskExecutionStatus.CREATED && queue.waiting().contains(last) }.orElse(false)) {
            logger.info("Trigger of task ${trigger.task?.name} skipped its turn, since its last run has not ended.")
            return
        }
        lastQueuedRun[key] = taskService.delegate(taskRequest).id
    }

    private fun startTriggers(currentTask: Task) {
        val threadPool = threadPoolMap[poolName(currentTask.name)]!!
        running[poolName(currentTask.name)] = currentTask.triggerActions
        currentTask.triggerActions.forEach { trigger ->
            val refusal = StepRules.active().refusalOf(trigger) ?: trigger.problem()
            if (refusal != null) {
                logger.warn("Trigger of task ${currentTask.name} did not start. $refusal")
                return@forEach
            }
            threadPool.submit(customers().bound {
                try {
                    // Shutdown decides, since code running inside a task can swallow the interrupt.
                    while (!threadPool.isShutdown) {
                        try {
                            fire(trigger)
                        } catch (e: Exception) {
                            if (threadPool.isShutdown) break
                            // One failure must not end a schedule, since nothing would start it again.
                            logger.error("Trigger of task ${currentTask.name} failed. Trying again.", e)
                            Thread.sleep(RETRY_AFTER_FAILURE_MS)
                        }
                    }
                } catch (e: InterruptedException) {
                    logger.info("Trigger of task ${currentTask.name} stopped.")
                }
            })
        }
    }

}

private val LOCAL_CUSTOMER = object : CustomerScope {}
