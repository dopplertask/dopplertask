package com.dopplertask.dopplertask.domain.action.ui

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.OutputType
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.ExpectedConditions
import org.openqa.selenium.support.ui.Select
import org.openqa.selenium.support.ui.WebDriverWait
import java.io.IOException
import java.util.function.Consumer
import javax.persistence.CascadeType
import javax.persistence.Column
import javax.persistence.DiscriminatorValue
import javax.persistence.Entity
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "BrowseWebAction")
@DiscriminatorValue("browseweb_action")
class BrowseWebAction : Action {
    @Column
    var url: String? = null

    @OneToMany(mappedBy = "browseWebAction", cascade = [CascadeType.ALL])
    private var actionList: List<UIAction> = ArrayList()

    @Column(columnDefinition = "BOOLEAN")
    var isHeadless = true

    constructor() {}
    constructor(url: String?) {
        this.url = url
    }

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
        val urlVariable = variableExtractorUtil.extract(url, execution, scriptLanguage)
        val actionResult = ActionResult()
        val os = System.getProperty("os.name")
        if (os.contains("Windows")) {
            System.setProperty(CHROME_DRIVER, "bin/chromedriver.exe")
        } else if (os.contains("Mac")) {
            System.setProperty(CHROME_DRIVER, "bin/chromedriver-mac")
        } else {
            System.setProperty(CHROME_DRIVER, "bin/chromedriver")
        }
        val chromeOptions = ChromeOptions()

        // Users of docker image usually uses headless mode so we will include additional arguments to support it.
        if (isHeadless) {
            chromeOptions.addArguments("--headless")
            chromeOptions.addArguments("--no-sandbox")
            chromeOptions.addArguments("--disable-gpu")
        }

        System.setProperty("webdriver.chrome.whitelistedIps", "");


        val webDriver: WebDriver = ChromeDriver(chromeOptions)
        val wait = WebDriverWait(webDriver, 10)
        // Open page
        webDriver[urlVariable]
        // Go through all actions
        for (uiAction in actionList) {
            val uiActionValueVariable = variableExtractorUtil.extract(
                if (uiAction.value != null) uiAction.value else "",
                execution,
                scriptLanguage
            )
            if (uiAction.action == UIActionType.WAIT) {
                try {
                    Thread.sleep(uiActionValueVariable.toLong())
                    actionResult.output =
                        actionResult.output + "Slept a specific amount of time [time=" + uiActionValueVariable + "]\n"
                } catch (e: Exception) {
                    actionResult.errorMsg = "Exception occurred during sleeping in UI Action"
                    actionResult.statusCode = StatusCode.FAILURE
                    return actionResult
                }
            } else if (uiAction.action == UIActionType.ACCEPT_ALERT) {
                try {
                    webDriver.switchTo().alert().accept()
                    actionResult.output = actionResult.output + "Accepted alert\n"
                } catch (e: Exception) {
                    actionResult.errorMsg = "Exception occured during accepting alert in UI Action"
                    actionResult.statusCode = StatusCode.FAILURE
                    return actionResult
                }
            } else { // Normal UI Actions
                val element = findWebElement(uiAction, wait, actionResult)
                if (actionResult.statusCode == StatusCode.FAILURE) {
                    webDriver.quit()
                    return actionResult
                }
                executeUIAction(uiAction.action, uiAction.fieldName, uiActionValueVariable, element, actionResult)
            }
        }
        webDriver.quit()
        actionResult.output = actionResult.output + "WebDriver executed successfully"
        actionResult.outputType = OutputType.STRING
        actionResult.statusCode = StatusCode.SUCCESS
        return actionResult
    }

    private fun findWebElement(uiAction: UIAction, wait: WebDriverWait, actionResult: ActionResult): WebElement? {
        var element: WebElement? = null
        try {
            element = when (uiAction.findByType) {
                UIFieldFindByType.ID -> wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(uiAction.fieldName)))
                UIFieldFindByType.NAME -> wait.until(ExpectedConditions.visibilityOfElementLocated(By.name(uiAction.fieldName)))
                UIFieldFindByType.XPATH -> wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath(uiAction.fieldName)))
                UIFieldFindByType.CSS -> wait.until(
                    ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector(
                            uiAction.fieldName
                        )
                    )
                )
            }
        } catch (e: Exception) { // Could not find element, ignore, add to action result
            actionResult.errorMsg = "Exception occured: $e"
            actionResult.statusCode = StatusCode.FAILURE
        }
        return element
    }

    private fun executeUIAction(
        uiActionType: UIActionType?,
        fieldName: String?,
        uiActionValueVariable: String,
        element: WebElement?,
        actionResult: ActionResult
    ) {
        if (element != null) {
            when (uiActionType) {
                UIActionType.PRESS -> {
                    element.click()
                    actionResult.output = actionResult.output + "Element has been clicked [element=" + fieldName + "]\n"
                }
                UIActionType.WRITE -> {
                    element.sendKeys(uiActionValueVariable)
                    actionResult.output =
                        actionResult.output + "Wrote text to an element [element=" + fieldName + ", text=" + uiActionValueVariable + "]\n"
                }
                UIActionType.SELECT -> {
                    (element as Select).selectByVisibleText(uiActionValueVariable)
                    actionResult.output =
                        actionResult.output + "Selected item from dropdown [element=" + fieldName + ", text=" + uiActionValueVariable + "]\n"
                }
                else -> {
                    actionResult.output + "Non-existant UI action type.\n"
                }
            }
        }
    }

    fun getActionList(): List<UIAction> {
        return actionList
    }

    fun setActionList(actionList: List<UIAction>) {
        this.actionList = actionList
        this.actionList.forEach(Consumer { uiAction: UIAction -> uiAction.browseWebAction = this })
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("url", "URL", PropertyInformationType.STRING, "", "URL of the web page"))
            actionInfo.add(
                PropertyInformation(
                    "headless",
                    "Headless mode",
                    PropertyInformationType.BOOLEAN,
                    "true",
                    "Start web driver without graphics"
                )
            )
            actionInfo.add(
                PropertyInformation(
                    "actionList", "Action list", PropertyInformationType.MAP, "", "", java.util.List.of(
                        PropertyInformation("fieldName", "Field name"),
                        PropertyInformation(
                            "action", "Action", PropertyInformationType.DROPDOWN, "PRESS", "", java.util.List.of(
                                PropertyInformation("PRESS", "Press"),
                                PropertyInformation("SELECT", "Select"),
                                PropertyInformation("WRITE", "Write"),
                                PropertyInformation("WAIT", "Wait"),
                                PropertyInformation("ACCEPT_ALERT", "Accept alert")
                            )
                        ),
                        PropertyInformation(
                            "findByType", "Find By Type", PropertyInformationType.DROPDOWN, "ID", "", java.util.List.of(
                                PropertyInformation("ID", "Id"),
                                PropertyInformation("NAME", "Name"),
                                PropertyInformation("XPATH", "XPath"),
                                PropertyInformation("CSS", "CSS")
                            )
                        ),
                        PropertyInformation("value", "Value")
                    )
                )
            )
            return actionInfo
        }

    override val description: String
        get() = "Browse the web and do GUI actions."

    companion object {
        private const val CHROME_DRIVER = "webdriver.chrome.driver"
    }
}