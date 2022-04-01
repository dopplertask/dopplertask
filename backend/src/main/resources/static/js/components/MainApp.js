import * as React from "react";
import RunTaskModal from "./RunTaskModal";
import EditActionModal from "./EditActionModal";
import SaveModal from "./SaveModal";
import OpenTaskModal from "./OpenTaskModal";
import TaskSettings from "./TaskSettings";
import ImportTaskModal from "./ImportTaskModal";

class MainApp extends React.Component {

    constructor(props) {
        super(props);
        let unsavedTaskNamePrefix = "_" + Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
        this.state = {
            availableActions: [],
            app: {},
            parameters: [],
            selectedAction: {userData: {customData: {}, lastSingleActionExecutionOutput: ""}},
            saveDialogVisible: false,
            taskName: "task" + unsavedTaskNamePrefix,
            description: "",
            start: false,
            saved: false,
            active: false
        }

        this.showNotification = this.showNotification.bind(this);
        this.executeAction = this.executeAction.bind(this);
        this.createNode = this.createNode.bind(this);
        this.prepareJSON = this.prepareJSON.bind(this);
        this.editModelForFigure = this.editModelForFigure.bind(this);
        this.saveActionSettings = this.saveActionSettings.bind(this);
        this.discardActionSettings = this.discardActionSettings.bind(this);
        this.saveWorkflow = this.saveWorkflow.bind(this);
        this.closeSaveDialog = this.closeSaveDialog.bind(this);
        this.handleSaveModalField = this.handleSaveModalField.bind(this);
        this.handleSaveModalDescriptionField = this.handleSaveModalDescriptionField.bind(this);
        this.downloadWorkflow = this.downloadWorkflow.bind(this);
        this.updateFieldData = this.updateFieldData.bind(this);
        this.setStart = this.setStart.bind(this);
        this.saveSettings = this.saveSettings.bind(this);
        this.openTask = this.openTask.bind(this);
        this.initApp = this.initApp.bind(this);
        this.newWorkflow = this.newWorkflow.bind(this);
        this.searchActions = this.searchActions.bind(this);
        this.importTask = this.importTask.bind(this);
        this.applyJSONToCanvas = this.applyJSONToCanvas.bind(this);
        this.generateString = this.generateString.bind(this);
        this.activateTask = this.activateTask.bind(this);
        this.populateCustomData = this.populateCustomData.bind(this);
    }

    executeAction() {
        // Call AJAX
        let ports = [];

        // Create ports
        this.state.selectedAction.outputPorts.data.forEach(value => {
            ports.push({externalId: value.name, portType: "OUTPUT"})
        });

        this.state.selectedAction.inputPorts.data.forEach(value => {
            ports.push({externalId: value.name, portType: "INPUT"})
        });

        let json = {
            ...this.state.selectedAction.userData.customData,
            "@type": this.state.selectedAction.userData.name,
            ports
        }
        let editor = ace.edit("editor_actionExecutionOutput")
        editor.setValue("Executing action...");
        $.ajax({
            type: "POST",
            url: "/task/action",
            data: JSON.stringify(json),
            contentType: 'application/json',
            success: success => {
                console.log(success);
                let output = "Last executed: " + new Date() + " No output. ";
                if (success.output != undefined && success.output != "") {
                    output = success.output;
                } else if (success.errorMsg != undefined && success.errorMsg != "") {
                    output = success.errorMsg;
                }
                this.setState(prevState => ({
                    selectedAction: {
                        ...prevState.selectedAction,
                        userData: {
                            ...prevState.selectedAction.userData,
                            lastSingleActionExecutionOutput: output
                        }
                    }
                }));


                editor.setValue(output);

            },
            dataType: "json"
        });
    }

    searchActions() {
        let input, filter, ul, li, a, i, txtValue;
        input = document.getElementById("actionSearchInput");
        filter = input.value.toUpperCase();
        ul = document.getElementById("actionSearchUL");
        li = ul.getElementsByTagName("a");
        for (i = 0; i < li.length; i++) {
            a = li[i].getElementsByTagName("div")[1];
            txtValue = a.textContent || a.innerText;
            if (txtValue.toUpperCase().indexOf(filter) > -1) {
                li[i].style.display = "";
            } else {
                li[i].style.display = "none";
            }
        }
    }

    setStart(start) {
        this.setState({
            start: start
        })
    }

    newWorkflow() {
        function createNewTask() {
            let unsavedTaskNamePrefix = "_" + Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2,
                15);
            this.state.app.view.clear();
            this.state.app.view.add(new StartFigure({
                x: 50,
                y: 340,
                width: 120,
                height: 120,
                userData: this.state.availableActions.find(
                    availableAction => availableAction.name == "StartAction")
            }));

            this.setState({
                taskName: "task" + unsavedTaskNamePrefix,
                parameters: [],
                saved: false
            });
        }

        if (!this.state.saved && confirm("You have unsaved changes. Do you want to continue?")) {
            createNewTask.call(this);
        } else if (this.state.saved) {
            createNewTask.call(this);
        }
    }

    populateCustomData(customData, propertyInformationList) {
        propertyInformationList.map(pi => {
            if (customData[pi.name] == undefined) {
                if (pi.type === "MAP") {
                    customData[pi.name] = []
                } else if (pi.type === "BOOLEAN") {
                    if (pi.defaultValue == "true") {
                        customData[pi.name] = true;
                    } else {
                        customData[pi.name] = false;
                    }
                } else {
                    customData[pi.name] = pi.defaultValue || "";
                }
            }

            if (pi.options != undefined) {
                this.populateCustomData(customData, pi.options)
            }
        });
    }

    componentDidMount() {
        let app = this.initApp();

        this.setState({app: app}, callback => {
            $.get("/task/actions", (data, status) => {
                let actions = [];
                data.actions.forEach(element => {
                    element.customData = [];
                    element.lastSingleActionExecutionOutput = "";

                    this.populateCustomData(element.customData, element.propertyInformationList)

                    actions.push(element);

                    // Auto add the start action
                    if (element.name == "StartAction") {
                        this.state.app.view.add(
                            new StartFigure({x: 50, y: 340, width: 120, height: 120, userData: element}))
                    }

                })

                // Set state.
                this.setState(prevState => ({
                    availableActions: actions
                }));

            });

        });

        $(".toast").toast({delay: 2000})

    }

    initApp() {
        let mainApp = this;
        var createConnection = function (sourcePort, targetPort) {

            let conn = new draw2d.Connection({
                router: new draw2d.layout.connection.InteractiveManhattanConnectionRouter(),
                color: "#334455",
                radius: 20,
                outlineColor: "#334455",
                source: sourcePort,
                target: targetPort,
                stroke: 2
            });

            return conn;

        };

        let app = new example.Application();

        app.view.installEditPolicy(new draw2d.policy.connection.DragConnectionCreatePolicy({
            createConnection: createConnection
        }));
        app.view.getCommandStack().on("change", function (e) {
            if (e.isPostChangeEvent()) {
                mainApp.setState({saved: false})
            }
        });
        return app;
    }

    generateString(length) {
        let result = '';
        let characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
        let charactersLength = characters.length;
        for (let i = 0; i < length; i++) {
            result += characters.charAt(Math.floor(Math.random() * charactersLength));
        }
        return result;
    }

    createNode(actionName, customData) {
        let action;
        let currentActionDetails = this.state.availableActions.find(availableAction => availableAction.name == actionName);

        // Make a copy to avoid using the same reference, overwriting each others values
        currentActionDetails = Object.assign({}, currentActionDetails);

        if(customData != undefined) {
            currentActionDetails.customData = customData;
        }

        if (actionName == "IfAction") {
            action = new IfAction({
                x: 550,
                y: 340,
                width: 120,
                height: 120,
                userData: currentActionDetails
            });
            action.onDoubleClick = () => this.editModelForFigure();
        } else if (actionName == "SwitchAction") {
            action = new SwitchAction({
                x: 550,
                y: 340,
                width: 120,
                height: 120,
                userData: currentActionDetails
            });
            action.onDoubleClick = () => this.editModelForFigure();
        } else if (actionName == "StartAction") {
            action = new StartFigure({
                x: 550,
                y: 340,
                width: 120,
                height: 120,
                userData: currentActionDetails
            });
            action.onDoubleClick = () => this.editModelForFigure();
        }
        else if (actionName == "LinkedTaskAction") {
            action = new LinkedActionTaskAction({
                x: 550,
                y: 340,
                width: 120,
                height: 120,
                userData: currentActionDetails
            });
            action.onDoubleClick = () => this.editModelForFigure();
        }
        else {
            if (currentActionDetails.trigger) {
                action = new StartFigure({
                    x: 550,
                    y: 340,
                    width: 120,
                    height: 120,
                    userData: currentActionDetails
                });
                action.onDoubleClick = () => this.editModelForFigure();
                action.userData.customData.triggerSuffix = this.generateString(10);
            } else {
                action = new BetweenFigure({
                    x: 550,
                    y: 340,
                    width: 120,
                    height: 120,
                    userData: currentActionDetails
                });
                action.onDoubleClick = () => this.editModelForFigure();
            }
        }

        return action;
    }

    discardActionSettings() {
        this.setState({selectedAction: {}});
    }

    prepareJSON(resultCallback) {
        let writer = new draw2d.io.json.Writer();
        let outputBody = {
            name: this.state.taskName,
            active: this.state.active,
            description: this.state.description,
            parameters: this.state.parameters,
            actions: [],
            connections: []
        };
        return new Promise(resolve => {
            writer.marshal(this.state.app.view, function (json) {
                for (var i = 0; i < json.length; i++) {
                    if (json[i].type == "draw2d.shape.node.Start") {
                        let currentActionPorts = [];
                        for (let j = 0; j < json[i].ports.length; j++) {
                            let decidedPortType = json[i].ports[j].type == "draw2d.OutputPort" ? "OUTPUT" : "INPUT";
                            currentActionPorts.push({externalId: json[i].ports[j].name, portType: decidedPortType})
                        }

                        outputBody.actions.push({
                            "@type": json[i].userData.name,
                            ...json[i].userData.customData,
                            ports: currentActionPorts,
                            guiXPos: json[i].x,
                            guiYPos: json[i].y
                        });
                    }
                    if (json[i].type == "draw2d.shape.node.Between") {
                        let currentActionPorts = [];
                        for (let j = 0; j < json[i].ports.length; j++) {
                            let decidedPortType = json[i].ports[j].type == "draw2d.OutputPort" ? "OUTPUT" : "INPUT";
                            currentActionPorts.push({externalId: json[i].ports[j].name, portType: decidedPortType})
                        }

                        outputBody.actions.push({
                            "@type": json[i].userData.name,
                            ...json[i].userData.customData,
                            ports: currentActionPorts,
                            guiXPos: json[i].x,
                            guiYPos: json[i].y
                        });
                    }
                    if (json[i].type == "draw2d.Connection") {
                        outputBody.connections.push(
                            {source: {externalId: json[i].source.port}, target: {externalId: json[i].target.port}});
                    }
                }

                resultCallback(JSON.stringify(outputBody, null, 2));
            });
        });
    }

    downloadWorkflow() {

        this.prepareJSON(json => {
            var a = document.createElement("a");
            var file = new Blob([json], {type: "text/plain"});
            a.href = URL.createObjectURL(file);
            a.download = "workflow.json";
            a.click();
        });

    }

    openTask(checksum) {
        let mainApp = this;
        $.ajax({
                type: "GET",
                url: "/task/" + checksum + "/checksum",
                contentType: 'application/json',
                success: task => {
                    this.applyJSONToCanvas(task, mainApp);
                }
                ,
                dataType: "json"
            }
        )
        ;
    }

    applyJSONToCanvas(task, mainApp) {
        this.state.app.view.clear();

        task.actions.forEach((action, i) => {
            let generatedAction = this.createNode(action["@type"], action);
            generatedAction.x = action.guiXPos;
            generatedAction.y = action.guiYPos;
            let inputPortIndex = 0;
            let outputPortIndex = 0;

            action.ports.forEach((port, i) => {
                if (port.portType == "INPUT") {
                    generatedAction.getInputPort(inputPortIndex).setId(port.externalId);
                    generatedAction.getInputPort(inputPortIndex).setName(port.externalId);
                    inputPortIndex++;
                } else {
                    if (generatedAction.getOutputPort(outputPortIndex) == undefined) {
                        generatedAction.createPort("output");
                    }
                    generatedAction.getOutputPort(outputPortIndex).setId(port.externalId);
                    generatedAction.getOutputPort(outputPortIndex).setName(port.externalId);
                    outputPortIndex++;
                }
            })

            this.state.app.view.add(generatedAction);
        })

        // Very inefficient
        // TODO: Improve performance
        task.connections.forEach(connection => {
            let conVisual = new draw2d.Connection({
                router: new draw2d.layout.connection.InteractiveManhattanConnectionRouter(),
                color: "#334455",
                radius: 20,
                outlineColor: "#334455",
                stroke: 2
            });

            this.state.app.view.figures.data.forEach(figure => {
                figure.outputPorts.data.forEach(outputPort => {
                        if (outputPort.name == connection.source.externalId) {
                            conVisual.setSource(outputPort);
                        }
                    }
                )
                figure.inputPorts.data.forEach(inputPort => {
                        if (inputPort.name == connection.target.externalId) {
                            conVisual.setTarget(inputPort);
                        }
                    }
                )
            })

            this.state.app.view.add(conVisual);
        })

        mainApp.setState({
            taskName: task.name,
            description: task.description,
            parameters: task.parameters,
            saved: true,
            active: task.active
        });
    }

    editModelForFigure() {
        this.setState({selectedAction: {}});
        let currentSelection = this.state.app.view.selection.all.data[0];
        this.setState({selectedAction: currentSelection});
        $("#actionEditModal").modal("show");
    }

    handleSaveModalField(event) {
        const target = event.target;
        let value = target.value;

        this.setState(prevState => ({
            taskName: value
        }));

        this.setState({saved: false});

    }

    handleSaveModalDescriptionField(event) {
        const target = event.target;
        let value = target.value;

        this.setState(prevState => ({
            description: value
        }));

        this.setState({saved: false});
    }

    saveActionSettings() {
        let currentSelection = this.state.app.view.selection.all.data[0];
        currentSelection.userData = this.state.selectedAction.userData;
        if (typeof currentSelection.updatePorts === "function") {
            currentSelection.updatePorts();
        }
        if(typeof currentSelection.updateFigureLabel === "function") {
            currentSelection.updateFigureLabel();
        }

        this.setState({saved: false});
    }

    render() {
        return <div id="container" style={{position: "relative", minHeight: "200px"}}>

            <div role="alert" aria-live="assertive" aria-atomic="true" className="toast" data-autohide="true"
                 style={{
                     position: "absolute",
                     left: "50%",
                     bottom: "10px",
                     transform: "translate(-50%,-50%)",
                     zIndex: 2000
                 }}
                 id="notificationAlert">
                <div className="toast-body" id="notificationAlertMessage">
                    Hello, world! This is a toast message.
                </div>
            </div>

            <div id="wrapper">

                <div id="sidebar-wrapper">
                    <nav id="spy">
                        <div className="sidebar-title">
                            <span>Actions</span>
                        </div>

                        <div className="sidebar-search">
                            <input type="text" id="actionSearchInput" placeholder="Search actions..."
                                   onKeyUp={() => this.searchActions()}
                                   className="form-control"/></div>


                        <div id="actionSearchUL">

                            {this.state.availableActions.map(action => (
                                <a href="#" className="item" key={action.name}
                                   onClick={() => {
                                       this.setState({saved: false});
                                       this.state.app.view.add(this.createNode(action.name))
                                   }}>
                                    <div className="menu-image"><img
                                        src={"images/actions/" + action.name + ".png"}/></div>
                                    <div className="menu-text">
                                        <div className="menu-text-title"><span>{action.name}</span></div>
                                        <div className="menu-text-detail">{action.description}</div>
                                    </div>
                                </a>
                            ))}
                        </div>

                    </nav>
                </div>

                <div id="page-content-wrapper">

                    <div className="row h-100 p-0 m-0">

                        <div className="col-sm-2 col-md-2 col-lg-2">
                            <nav id="spy">
                                <div className="sidebar-brand">
                                        <span><img src="images/logo.png" alt=""
                                        /></span>
                                </div>
                                <div className="text-center"><small>Version: 0.14.0</small></div>
                                <ul className="sidebar-nav nav">

                                    <li><a href="#" onClick={this.newWorkflow}><i className="fas fa-file"></i> New task</a>
                                    </li>
                                    <li><a href="#" onClick={() => $("#openTaskModal").modal("show")}><i
                                        className="fas fa-folder-open"></i> Open
                                        Task</a></li>
                                    <li><a href="#" onClick={this.saveWorkflow}
                                    ><i
                                        className="fas fa-save"></i> Save</a></li>
                                    <li><a href="#" onClick={() => $("#saveModal").modal("show")}
                                    ><i
                                        className="fas fa-copy"></i> Save as</a></li>


                                    <li><a href="#" onClick={this.downloadWorkflow}><i
                                        className="fas fa-file-export"></i> Export
                                        Task</a>
                                    </li>

                                    <li><a href="#" onClick={() => $("#importTaskModal").modal("show")}
                                    ><i
                                        className="fas fa-file-import"></i> Import Task</a>
                                    </li>
                                    <li><a href="#" onClick={() => $("#taskSettingsModal").modal("show")}
                                    ><i className="fas fa-cog"></i> Task parameters</a>
                                    </li>
                                    <li>
                                        <a href="#" onClick={() => $("#wrapper").toggleClass("active")}
                                        ><i className="fas fa-toggle-on"></i> Show/hide
                                            actions</a>
                                    </li>
                                </ul>
                                <div><a href="#" onClick={() => {
                                    $("#runTaskModal").modal("show");

                                }} className="btn btn-primary btn-block">Run
                                    Workflow</a></div>
                            </nav>

                            <br/>
                            Current task:<br/>
                            {!this.state.saved ? ("[UNSAVED]") : ("")} {this.state.taskName}

                            <br/><br/>


                            <label className="custom-control custom-switch">
                                <input type="checkbox" onChange={this.activateTask}
                                       autoComplete="off" id="customSwitches" className="custom-control-input"
                                       checked={this.state.active ? true : false}/>
                                <label className="custom-control-label" htmlFor="customSwitches">Activated
                                    triggers</label>
                            </label>

                        </div>
                        <div className="col-sm-10 col-md-10 col-lg-10 m-0 p-0">
                            <div id="canvas" className="w-100 h-100"></div>
                        </div>

                    </div>
                    <div id="content">


                    </div>
                </div>

            </div>


            <EditActionModal taskName={this.state.taskName} updateFieldData={this.updateFieldData}
                             executeAction={this.executeAction}
                             selectedAction={this.state.selectedAction}
                             taskParameters={this.state.parameters}
                             discardActionSettings={this.discardActionSettings}/>

            <SaveModal
                saveWorkflow={this.saveWorkflow} closeSaveDialog={this.closeSaveDialog}
                taskName={this.state.taskName}
                description={this.state.description}
                handleSaveModalField={this.handleSaveModalField} handleSaveModalDescriptionField={this.handleSaveModalDescriptionField}/>

            <RunTaskModal prepareJSON={this.prepareJSON} saved={this.state.saved}
                          taskName={this.state.taskName} start={this.state.start}
                          parameters={this.state.parameters} setStart={this.setStart}/>

            <OpenTaskModal
                openTask={this.openTask} saved={this.state.saved}/>

            <TaskSettings parameters={this.state.parameters}
                          saveSettings={this.saveSettings}
            />

            <ImportTaskModal importTask={this.importTask}/>

        </div>
            ;
    }

    showNotification(msg) {
        $("#notificationAlertMessage").html(msg)
        $("#notificationAlert").toast('show');
    }

    importTask() {
        if (!this.state.saved) {
            if (confirm("You have an unsaved workflow. Do you want to continue anyway?")) {
                let editor = ace.edit("editor_importTaskEditor");
                try {
                    let importTaskObj = JSON.parse(editor.getValue());

                    this.applyJSONToCanvas(importTaskObj, this);

                    $("#importTaskModal").modal("hide");
                    this.showNotification("Task has been imported!");
                } catch (e) {
                    this.showNotification("Could not import task. Wrong JSON!");
                }

            }
        } else {
            let editor = ace.edit("editor_importTaskEditor");
            try {
                let importTaskObj = JSON.parse(editor.getValue());

                this.applyJSONToCanvas(importTaskObj, this);

                $("#importTaskModal").modal("hide");
                this.showNotification("Task has been imported!");
            } catch (e) {
                this.showNotification("Could not import task. Wrong JSON!");
            }

        }
    }

    saveSettings(parameters) {
        this.setState({
            parameters: parameters
        })

    }

    saveWorkflow() {
        // Validation before sending to API.
        let startActions = 0;
        this.state.app.view.figures.data.forEach(figure => {
            if (figure instanceof StartFigure && figure.userData.name == "StartAction") {
                startActions++;
            }
        })

        // Validate
        if (startActions > 1) {
            this.showNotification("Only one start action is allowed.");
            return;
        }
        else if(startActions < 1) {
            this.showNotification("A start action is always needed.");
            return;
        }

        this.prepareJSON(json => {
            console.log(json);
            $.ajax({
                type: "POST",
                url: "/task",
                data: json,
                contentType: 'application/json',
                success: success => {
                    this.setState({saved: true})
                    this.showNotification("Task saved!");
                },
                dataType: "json"
            });
        });

    }

    closeSaveDialog() {
        this.setState({saveDialogVisible: false});
    }

    updateFieldData(id, value) {
        console.log("Updating field: " + id + " value: " + value)
        this.setState(prevState => ({
            selectedAction: {
                ...prevState.selectedAction,
                userData: {
                    ...prevState.selectedAction.userData,
                    customData: {
                        ...prevState.selectedAction.userData.customData,
                        [id]: value
                    }
                }
            }
        }), () => {
            this.saveActionSettings();
        });

    }

    activateTask(event) {
        this.setState({
            active: event.target.checked ? true : false
        }, event => {
            this.saveWorkflow();
        })
    }
}

export default MainApp;