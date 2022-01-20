package com.dopplertask.dopplertask.domain.action.ui

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.awt.AWTException
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.IOException
import javax.persistence.*

@Entity
@Table(name = "MouseAction")
@DiscriminatorValue("mouse_action")
class MouseAction : Action() {
    @Enumerated(EnumType.STRING)
    @Column
    var action: MouseActionType? = null
    var positionX: String? = null
    var positionY: String? = null
    var button: String? = null

    @Throws(IOException::class)
    override fun run(taskService: TaskService, execution: TaskExecution, variableExtractorUtil: VariableExtractorUtil, broadcastListener: BroadcastListener?): ActionResult {
        val localPositionX = variableExtractorUtil.extract(positionX, execution, scriptLanguage)
        val localPositionY = variableExtractorUtil.extract(positionY, execution, scriptLanguage)
        val localButton = variableExtractorUtil.extract(button, execution, scriptLanguage)
        System.setProperty("java.awt.headless", "false")
        val actionResult = ActionResult()
        var selectedButton = InputEvent.BUTTON1_DOWN_MASK
        if (localButton == "LEFT") {
            selectedButton = InputEvent.BUTTON1_DOWN_MASK
        } else if (localButton == "RIGHT") {
            selectedButton = InputEvent.BUTTON2_DOWN_MASK
        }
        if (action == null) {
            actionResult.statusCode = StatusCode.FAILURE
            actionResult.errorMsg = "Missing action. Ensure that you've entered a supported action."
            return actionResult
        }
        if (action == MouseActionType.MOVE && localPositionX == "" && localPositionY == "") {
            actionResult.statusCode = StatusCode.FAILURE
            actionResult.errorMsg = "No X and/or Y position was provided."
            return actionResult
        }
        try {
            val robot = Robot()
            when (action) {
                MouseActionType.MOVE -> if (localPositionX != null && localPositionY != null) {
                    robot.mouseMove(localPositionX.toInt(), localPositionY.toInt())
                    actionResult.output = "Mouse was moved to X: $localPositionX, Y: $localPositionY"
                } else {
                    actionResult.statusCode = StatusCode.FAILURE
                    actionResult.errorMsg = "Positions for the mouse were not provided."
                    return actionResult
                }
                MouseActionType.CLICK -> {
                    robot.mousePress(selectedButton)
                    robot.mouseRelease(selectedButton)
                    actionResult.output = "Mouse $selectedButton was clicked."
                }
                MouseActionType.PRESS -> {
                    robot.mousePress(selectedButton)
                    actionResult.output = "Mouse $selectedButton was pressed."
                }
                MouseActionType.RELEASE -> {
                    robot.mouseRelease(selectedButton)
                    actionResult.output = "Mouse $selectedButton was released."
                }
                else -> {
                }
            }
        } catch (e: AWTException) {
            throw RuntimeException(e)
        } catch (e: NumberFormatException) {
            throw RuntimeException(e)
        }
        return actionResult
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("button", "Button", PropertyInformationType.DROPDOWN, "LEFT", "Button to press", listOf(
                    PropertyInformation("LEFT", "Left click"),
                    PropertyInformation("RIGHT", "Right click")
            )))
            actionInfo.add(PropertyInformation("action", "Action", PropertyInformationType.DROPDOWN, "MOVE", "", listOf(
                    PropertyInformation("MOVE", "Move", mutableListOf(
                        PropertyInformation("positionX", "Position X", PropertyInformationType.STRING, "0", "Move mouse to this X pos."),
                        PropertyInformation("positionY", "Position Y", PropertyInformationType.STRING, "0", "Move mouse to this Y pos.")
                    )),
                    PropertyInformation("CLICK", "Click"),
                    PropertyInformation("PRESS", "Press"),
                    PropertyInformation("RELEASE", "Release")
            )))
            return actionInfo
        }

    override val description: String
        get() = "Operate the mouse."

}