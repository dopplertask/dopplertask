package com.dopplertask.dopplertask.service

import com.dopplertask.dopplertask.dao.TaskDao
import com.dopplertask.dopplertask.domain.Task
import com.dopplertask.dopplertask.domain.action.trigger.Trigger
import org.slf4j.LoggerFactory
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
open class TriggerListenerServiceImpl : TriggerListenerService {

    private val logger = LoggerFactory.getLogger(TriggerListenerServiceImpl::class.java)
    private val threadPoolMap: MutableMap<String, ExecutorService>

    private val taskDao: TaskDao
    private val taskService: TaskService

    private val tasks: MutableSet<Task> = mutableSetOf()


    @Autowired
    @Qualifier("transactionManager")
    protected lateinit var txManager: PlatformTransactionManager

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
        tmpl.executeWithoutResult { startActiveTriggers() }

    }

    override fun startActiveTriggers() {
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
            // By default, maximum of 40 active triggers allowed for each Task
            threadPoolMap[task.name] = Executors.newFixedThreadPool(40)

            startTriggers(task)
        }

    }

    override fun updateTriggers(id: Long) {
        val task = taskDao.findById(id);
        if (task.isPresent) {
            val currentTask = task.get();

            // Initiate map if not existant
            if (threadPoolMap[currentTask.name] != null) {
                // By default, maximum of 40 active triggers allowed for each Task
                threadPoolMap[currentTask.name]!!.shutdownNow()
            }

            if (currentTask.isActive) {
                threadPoolMap[currentTask.name] = Executors.newFixedThreadPool(40)

                startTriggers(currentTask)
            }
        }

    }

    override fun stopTriggers(taskName: String) {
        threadPoolMap.remove(taskName)?.shutdownNow()
    }

    private fun fire(trigger: Trigger) {
        logger.info("Running trigger")
        val triggerResult = trigger.trigger()

        val taskRequest = TaskRequest()
        taskRequest.triggerInfo = TriggerInfo(trigger.javaClass.simpleName, trigger.triggerSuffix, triggerResult.resultMap)
        taskRequest.taskName = trigger.task?.name
        taskRequest.parameters = mutableMapOf()

        taskService.runRequest(taskRequest)
    }

    private fun startTriggers(currentTask: Task) {
        val threadPool = threadPoolMap[currentTask.name]!!
        currentTask.triggerActions.forEach { trigger ->
            threadPool.submit {
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
            }
        }
    }

}