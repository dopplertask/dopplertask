import * as React from "react";

class RunTaskModal extends React.Component {

    constructor(props) {
        super(props);
        this.renderOutput = this.renderOutput.bind(this);
        this.cleanup = this.cleanup.bind(this);
        this.runTask = this.runTask.bind(this);
        this.parameterChange = this.parameterChange.bind(this);
        this.state = {
            taskName: this.props.taskName,
            start: this.props.start,
            executionId: -1,
            parameterValues: {},
            saved: this.props.saved
        }
    }

    componentDidMount() {
        this.setState(
            {
                taskName: this.props.taskName,
                start: this.props.start,
                saved: this.props.saved
            })
    }

    componentDidUpdate(prevProps, prevState, snapshot) {

        if (prevProps.taskName != this.props.taskName) {
            this.setState(
                {
                    taskName: this.props.taskName
                })
        }

        if (prevProps.saved != this.props.saved) {
            this.setState(
                {
                    saved: this.props.saved
                })
        }

        if (prevProps.start != this.props.start) {
            this.setState(
                {
                    start: this.props.start
                }, () => {
                    if (this.props.start) {
                        jQuery("#outputDiv").empty();
                        this.renderOutput()
                    }
                })

        }
    }

    runTask() {
        if (!this.state.start) {
            this.props.setStart(true);
        } else {
            this.props.setStart(false);
        }
    }

    parameterChange(event) {
        const target = event.target;
        let value = target.value;

        if (value.length == 0) {
            value = undefined;
        }
        this.setState(prevState => ({
            parameterValues: {
                ...prevState.parameterValues,
                [target.id]: value
            }
        }));
    }

    renderOutput() {
        if (this.state.start) {
            let json = {
                taskName: this.state.taskName,
                parameters: this.state.parameterValues
            }

            if (!this.state.saved) {
                this.props.prepareJSON(jsonResult => {
                    json.task = JSON.parse(jsonResult);
                    this.requestRun(json);
                });
            } else {
                this.requestRun(json);
            }

        }
    }

    requestRun(json) {
        let runTaskModal = this;
        $.ajax({
                   type: "POST",
                   url: "/schedule/task",
                   data: JSON.stringify(json),
                   contentType: 'application/json',
                   success: execution => {
                       runTaskModal.setState({
                                                 executionId: execution.id
                                             }, createStomp => {
                           let client = Stomp.client("ws://localhost:61614/stomp", "v11.stomp");
                           let headers = {
                               id: 'JUST.FCX',
                               ack: 'client',
                               selector: 'executionId=' + runTaskModal.state.executionId
                           };
                           client.connect("admin", "admin", function () {
                               client.subscribe("/queue/taskexecution_destination",
                                                function (message) {
                                                    let messageBody = JSON.parse(message.body);
                                                    var tagsToReplace = {
                                                        '&': '&amp;',
                                                        '<': '&lt;',
                                                        '>': '&gt;'
                                                    };

                                                    function replaceTag(tag) {
                                                        return tagsToReplace[tag] || tag;
                                                    }

                                                    function safe_tags_replace(str) {
                                                        return str.replace(/[&<>]/g, replaceTag);
                                                    }

                                                    jQuery("#outputDiv").append(safe_tags_replace(messageBody.output) + "<br>");
                                                    message.ack();

                                                    if (message.headers["lastMessage"] == "true"
                                                        && message.headers["executionId"] == runTaskModal.state.executionId) {
                                                        client.disconnect();
                                                        runTaskModal.cleanup();
                                                    }
                                                }, headers);
                           });
                       })
                   },
                   dataType: "json"
               });
    }

    cleanup() {
        this.props.setStart(false);
    }

    render() {
        return (<div className="modal fade" id="runTaskModal" tabIndex="-1" role="dialog"
                     aria-labelledby="runTaskModalLabel" aria-hidden="true">
            <div className="modal-dialog modal-lg" role="document">
                <div className="modal-content">
                    <div className="modal-header">
                        <h5 className="modal-title" id="runTaskModalLabel">Run task</h5>
                        <button type="button" className="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    <div className="modal-body" id="runTaskModalBody">
                        <h4>Task execution</h4>

                        <h5>Parameter values</h5>
                        <div className="container">

                            {
                                this.props.parameters.map(parameter => (
                                    <div className="row" key={parameter.name}>
                                        <div className="col-sm">
                                            {parameter.name}
                                        </div>
                                        <div className="col-sm">
                                            {parameter.description}
                                        </div>
                                        <div className="col-sm">
                                            <input className="form-control"
                                                   type="text"
                                                   placeholder={parameter.defaultValue}
                                                   id={parameter.name}
                                                   onChange={this.parameterChange}
                                            />
                                        </div>

                                    </div>
                                ))
                            }

                        </div>

                        <h5>Output</h5>
                        <br/>
                        <pre id="outputDiv" className="code-pre">Run the task by clicking the "Run task" button.</pre>


                    </div>

                    <div className="modal-footer">
                        <button type="button" className="btn btn-secondary" data-dismiss="modal"
                                onClick={this.props.closeSaveDialog}>Close
                        </button>
                        <button type="button" className={"btn btn-" + (this.state.start ? "danger" : "primary")}
                                onClick={this.runTask}>{this.state.start ? "Stop Task" : "Run task"}
                        </button>
                    </div>
                </div>
            </div>
        </div>);

    }

}

export default RunTaskModal;