package com.dopplertask.dopplertask.service

import com.dopplertask.dopplertask.dao.TaskDao
import com.dopplertask.dopplertask.domain.Task
import com.dopplertask.dopplertask.domain.action.trigger.Trigger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
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


    @Autowired
    @Qualifier("transactionManager")
    protected lateinit var txManager: PlatformTransactionManager

    @Autowired
    protected lateinit var customerScope: ObjectProvider<CustomerScope>

    @Autowired
    protected lateinit var runQueue: ObjectProvider<RunQueue>

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
            threadPoolMap[poolName(task.name)] = Executors.newVirtualThreadPerTaskExecutor()

            startTriggers(task)
        }

    }

    open fun updateTriggers(id: Long) {
        val task = taskDao.findById(id);
        if (task.isPresent) {
            val currentTask = task.get();

            // Initiate map if not existant
            if (threadPoolMap[poolName(currentTask.name)] != null) {
                    threadPoolMap[poolName(currentTask.name)]!!.shutdownNow()
            }

            if (currentTask.isActive) {
                threadPoolMap[poolName(currentTask.name)] = Executors.newVirtualThreadPerTaskExecutor()

                startTriggers(currentTask)
            }
        }

    }

    open fun stopTriggers(taskName: String) {
        threadPoolMap.remove(poolName(taskName))?.shutdownNow()
    }

    /** Every trigger of the current scope, such as when the scope goes away. */
    open fun stopAllTriggers() {
        val prefix = customers().current() + "/"
        threadPoolMap.keys.filter { it.startsWith(prefix) }.forEach { threadPoolMap.remove(it)?.shutdownNow() }
    }

    private fun customers(): CustomerScope = if (::customerScope.isInitialized) customerScope.getIfAvailable { LOCAL_CUSTOMER } else LOCAL_CUSTOMER

    private fun poolName(taskName: String?) = customers().current() + "/" + taskName

    private fun fire(trigger: Trigger) {
        logger.info("Running trigger")
        val triggerResult = trigger.trigger()

        val taskRequest = TaskRequest()
        taskRequest.triggerInfo = TriggerInfo(trigger.javaClass.simpleName, trigger.triggerSuffix, triggerResult.resultMap)
        taskRequest.taskName = trigger.task?.name
        taskRequest.parameters = mutableMapOf()

        // A queue that an add-on supplies decides when runs go and how many each customer has, so a schedule waits there
        // too. Without one, the run goes here, and the next one waits for it.
        if (::runQueue.isInitialized && runQueue.getIfAvailable() != null) taskService.delegate(taskRequest) else taskService.runRequest(taskRequest)
    }

    private fun startTriggers(currentTask: Task) {
        val threadPool = threadPoolMap[poolName(currentTask.name)]!!
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
