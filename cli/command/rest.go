package command

import (
	"errors"
	"io/ioutil"
	"os"
	"path"
	"strconv"

	"gopkg.in/resty.v1"
)

type buildTaskResponse struct {
	Checksum string `json:"checksum"`
}

type errorResponse struct {
	Timestamp string `json:"timestamp"`
	Status    int    `json:"status"`
	Error     string `json:"error"`
	Message   string `json:"message"`
	Path      string `json:"path"`
}

type runTaskResponse struct {
	Output []string `json:"output"`
}

type scheduleTaskResponse struct {
	ID string `json:"id"`
}

type executionsResponse struct {
	ExecutionID string `json:"executionId"`
	TaskName    string `json:"taskName"`
	Status      string `json:"status"`
	StartDate   string `json:"startDate"`
	EndDate     string `json:"endDate"`
}

type taskResponse struct {
	TaskID   int    `json:"id"`
	TaskName string `json:"name"`
	Created  string `json:"created"`
	Checksum string `json:"checksum"` // TODO: Implement in backend
	Actions  string `json:"actions"`
}

type executionListResponse struct {
	Executions []executionsResponse
}

type pushTaskResponse struct {
	TaskName string `json:"taskName"`
}

type simpleMessageResponse struct {
	Message string `json:"message"`
}

func buildTask(buildOpts *buildOptions) (resp *buildTaskResponse, err error) {

	url := buildOpts.commonOptions.dopplerURL + "task"
	filePath := path.Join(buildOpts.filePath, buildOpts.fileName)
	file, err := os.Open(filePath)
	if err != nil {
		return
	}
	fileBytes, err := ioutil.ReadAll(file)
	if err != nil {
		return
	}

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetBody(fileBytes).
		SetContentLength(true).
		SetResult(&buildTaskResponse{}).
		SetError(&errorResponse{}).
		Post(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*buildTaskResponse)

	return
}

func runTask(runOpts *runOptions) (resp *scheduleTaskResponse, err error) {

	url := runOpts.commonOptions.dopplerURL + "schedule/task"

	postBody := map[string]interface{}{
		"taskName":   runOpts.taskName,
		"parameters": runOpts.parameters,
	}

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetBody(postBody).
		SetContentLength(true).
		SetResult(&scheduleTaskResponse{}).
		Post(url)

	resp = response.Result().(*scheduleTaskResponse)

	return
}

func getExecutions(processOpts *processOptions) (resp *executionListResponse, err error) {

	url := processOpts.commonOptions.dopplerURL + "executions"

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetContentLength(true).
		SetResult(&executionListResponse{}).
		SetError(&errorResponse{}).
		Get(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*executionListResponse)

	return
}

// Request Example
// http://localhost:8090/task GET
func getTasks(processOpts *processOptions) (resp *[]taskResponse, err error) {

	url := processOpts.commonOptions.dopplerURL + "task"

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetContentLength(true).
		SetResult(&[]taskResponse{}).
		SetError(&errorResponse{}).
		Get(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*[]taskResponse)

	return
}

// Request Example
// http://localhost:8090/task/push POST
// Body: {"taskName":"asdsadf"}
func pushTask(pushOpts *pushOptions) (resp *pushTaskResponse, err error) {

	url := pushOpts.commonOptions.dopplerURL + "task/push"

	postBody := map[string]interface{}{
		"taskName": pushOpts.taskName,
	}

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetBody(postBody).
		SetContentLength(true).
		SetResult(&pushTaskResponse{}).
		SetError(&errorResponse{}).
		Post(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*pushTaskResponse)

	return
}

func pullTask(pullOpts *pullOptions) (resp *buildTaskResponse, err error) {

	url := pullOpts.commonOptions.dopplerURL + "task/download"

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetQueryParam("taskName", pullOpts.taskName).
		SetContentLength(true).
		SetResult(&buildTaskResponse{}).
		SetError(&errorResponse{}).
		Get(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*buildTaskResponse)

	return
}

// Request Example
// http://localhost:8090/task/alert_alert?forceDelete=false DELETE
func deleteTask(deleteTaskOpts *deleteTaskOptions) (resp *simpleMessageResponse, err error) {

	url := deleteTaskOpts.commonOptions.dopplerURL + "task/{taskNameOrChecksum}"

	pathParams := map[string]string{
		"taskNameOrChecksum": deleteTaskOpts.taskNameOrChecksum,
	}

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetPathParams(pathParams).
		SetQueryParam("forceDelete", strconv.FormatBool(deleteTaskOpts.forceDelete)).
		SetContentLength(true).
		SetResult(&simpleMessageResponse{}).
		SetError(&errorResponse{}).
		Delete(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*simpleMessageResponse)

	return
}

// Request Example
// http://localhost:8090/execution/1 DELETE
func deleteExecution(deleteExecutionOpts *deleteExecutionOptions) (resp *executionsResponse, err error) {

	url := deleteExecutionOpts.commonOptions.dopplerURL + "execution/{executionID}"

	pathParams := map[string]string{
		"executionID": deleteExecutionOpts.executionID,
	}

	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetPathParams(pathParams).
		SetContentLength(true).
		SetResult(&executionsResponse{}).
		SetError(&errorResponse{}).
		Delete(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*executionsResponse)

	return
}

// Request Example
// http://localhost:8090/task/rename POST
// Body: {"sourceTaskName" : "run-alert", "targetTaskName" : "alert"}
func renameTask(renameTaskOpts *renameTaskOptions) (resp *taskResponse, err error) {

	url := renameTaskOpts.commonOptions.dopplerURL + "/task/rename"

	postBody := map[string]interface{}{
		"sourceTaskName": renameTaskOpts.sourceTaskName,
		"targetTaskName": renameTaskOpts.targetTaskName,
	}
	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetBody(postBody).
		SetContentLength(true).
		SetResult(&taskResponse{}).
		SetError(&errorResponse{}).
		Post(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*taskResponse)

	return
}

// Request Example
// http://localhost:8090/login POST
// Body: {"username" : "xxx", "password" : "xxx"}
func login(loginOpts *loginOptions) (resp *simpleMessageResponse, err error) {

	url := loginOpts.commonOptions.dopplerURL + "/login"

	postBody := map[string]interface{}{
		"username": loginOpts.username,
		"password": loginOpts.password,
	}
	response, err := resty.R().
		SetHeader("Content-Type", "application/json").
		SetBody(postBody).
		SetContentLength(true).
		SetResult(&simpleMessageResponse{}).
		SetError(&errorResponse{}).
		Post(url)

	if response.IsError() {
		err = errors.New(response.Error().(*errorResponse).Message)
		return
	}

	resp = response.Result().(*simpleMessageResponse)

	return
}
