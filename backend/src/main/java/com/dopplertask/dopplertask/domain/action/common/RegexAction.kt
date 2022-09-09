package com.dopplertask.dopplertask.domain.action.common

import com.dopplertask.dopplertask.domain.ActionResult
import com.dopplertask.dopplertask.domain.StatusCode
import com.dopplertask.dopplertask.domain.TaskExecution
import com.dopplertask.dopplertask.domain.action.Action
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationType
import com.dopplertask.dopplertask.service.BroadcastListener
import com.dopplertask.dopplertask.service.TaskService
import com.dopplertask.dopplertask.service.VariableExtractorUtil
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.persistence.*


@Entity
@Table(name = "RegexAction")
@DiscriminatorValue("regex_action")
class RegexAction : Action {
    @Lob
    @Column(columnDefinition = "TEXT")
    var regexPattern: String = ""

    @Lob
    @Column(columnDefinition = "TEXT")
    var stringToCheck: String = ""

    constructor() {}
    constructor(regex: String, text: String) {
        this.regexPattern = regex
        this.stringToCheck = text
    }

    @Throws(IOException::class)
    override fun run(
        taskService: TaskService,
        execution: TaskExecution,
        variableExtractorUtil: VariableExtractorUtil,
        broadcastListener: BroadcastListener?
    ): ActionResult {
     //   val regexVariable = variableExtractorUtil.extract(regexPattern, execution, scriptLanguage)
        val textVariable = variableExtractorUtil.extract(stringToCheck, execution, scriptLanguage)
        val actionResult = ActionResult()

        val matches = getMatches(textVariable, regexPattern);
        if (regexPattern.isNotEmpty()) {
            actionResult.output = "Matches: " + matches.size +" \n" + matches.joinToString(",")
            actionResult.outputVariables["matches"] = matches;
            actionResult.statusCode = StatusCode.SUCCESS
        } else {
            actionResult.errorMsg = "Regex is empty. Please specify a regex."
            actionResult.statusCode = StatusCode.FAILURE
        }
        return actionResult
    }

    private fun getMatches(stringToCheck: String?, pattern: String?): MutableList<String> {
        val matches: MutableList<String> = ArrayList()
        val m: Matcher = Pattern.compile(pattern).matcher(stringToCheck)
        while (m.find()) {
            matches.add(m.group(1))
        }
        return matches
    }

    override val actionInfo: MutableList<PropertyInformation>
        get() {
            val actionInfo = super.actionInfo
            actionInfo.add(PropertyInformation("stringToCheck", "Text", PropertyInformationType.MULTILINE))
            actionInfo.add(PropertyInformation("regexPattern", "Regex pattern", PropertyInformationType.STRING))
            return actionInfo
        }

    override val description: String = "Extracts text using regex"
}