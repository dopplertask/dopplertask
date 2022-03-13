import * as React from "react";

class TaskSettings extends React.Component {

    constructor(props) {
        super(props);
        this.nameValueChange = this.nameValueChange.bind(this);
        this.requiredValueChange = this.requiredValueChange.bind(this);
        this.defaultValueChange = this.defaultValueChange.bind(this);
        this.descriptionValueChange = this.descriptionValueChange.bind(this);
        this.addTaskParameter = this.addTaskParameter.bind(this)
        this.removeTaskParameter = this.removeTaskParameter.bind(this)
        this.saveSettings = this.saveSettings.bind(this);
        this.discardSettings = this.discardSettings.bind(this);
        this.state = {
            parameters: []
        }
    }

    componentDidMount() {
        let taskSettings = this;
        this.setState({
                          parameters: this.props.parameters
                      })

        $('#taskSettingsModal').on('hidden.bs.modal', function (e) {
            taskSettings.setState({
                                      parameters: taskSettings.props.parameters
                                  })
        })
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        if (prevProps.parameters != this.props.parameters) {
            this.setState({
                              parameters: this.props.parameters
                          })
        }
    }

    discardSettings() {
        this.setState({
                          parameters: this.props.parameters
                      })
    }

    saveSettings() {
        let names = [];
        this.state.parameters.forEach(e => names.push(e.name));

        if (new Set(names).size !== names.length) {
            alert("Cannot have duplicate parameter names.")
        } else {
            this.props.saveSettings(this.state.parameters);
            $('#taskSettingsModal').modal("hide")
        }
    }

    removeTaskParameter(index) {
        this.state.parameters.splice(index, 1);
        this.forceUpdate();
    }

    addTaskParameter() {
        this.setState(prevState => ({
            parameters: [
                ...prevState.parameters,
                {
                    name: "new_parameter",
                    defaultValue: "",
                    required: false,
                    description: ""
                }
            ]
        }));
    }

    nameValueChange(event, i) {
        const target = event.target;
        let value = target.value;
        this.state.parameters[i].name = value;
        this.forceUpdate();
    }

    descriptionValueChange(event, i) {
        const target = event.target;
        let value = target.value;
        this.state.parameters[i].description = value;
        this.forceUpdate();
    }

    defaultValueChange(event, i) {
        const target = event.target;
        let value = target.value;
        this.state.parameters[i].defaultValue = value;
        this.forceUpdate();
    }

    requiredValueChange(event, i) {
        const target = event.target;
        let value = target.checked;

        this.state.parameters[i].required = value;
        this.forceUpdate();
    }

    render() {
        return (<div className="modal fade" id="taskSettingsModal" tabIndex="-1" role="dialog"
                     aria-labelledby="taskSettingsModalLabel" aria-hidden="true">
            <div className="modal-dialog modal-lg" role="document">
                <div className="modal-content">
                    <div className="modal-header">
                        <h5 className="modal-title" id="taskSettingsModalLabel">Task parameters</h5>
                        <button type="button" className="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    <div className="modal-body" id="taskSettingsModalBody">
                        Add parameters that will be needed by the task.

                        <button type="button" className="btn btn-primary float-right" onClick={this.addTaskParameter}>Add
                            Parameter
                        </button>

                        <table className="table table-hover">
                            <thead>
                            <tr>
                                <th scope="col">Name</th>
                                <th scope="col">Description</th>
                                <th scope="col">Default value</th>
                                <th scope="col">Required</th>
                                <th scope="col">Action</th>
                            </tr>
                            </thead>
                            <tbody>

                            {
                                this.state.parameters.map((taskParameter, i) => {
                                    return (
                                        <tr>
                                            <td><input className="form-control"
                                                       type="text"
                                                       id={taskParameter.name + "_name"}
                                                       value={taskParameter.name}
                                                       onChange={(e) => this.nameValueChange(e, i)}
                                            /></td>
                                            <td><input className="form-control"
                                                       type="text"
                                                       id={taskParameter.name + "_description"}
                                                       value={taskParameter.description}
                                                       onChange={(e) => this.descriptionValueChange(e, i)}
                                            /></td>
                                            <td><input className="form-control"
                                                       type="text"
                                                       id={taskParameter.name + "_defaultValue"}
                                                       value={taskParameter.defaultValue}
                                                       onChange={(e) => this.defaultValueChange(e, i)}
                                            /></td>
                                            <td><input type="checkbox"
                                                       id={taskParameter.name + "_required"}
                                                       checked={taskParameter.required || false}
                                                       onChange={(e) => this.requiredValueChange(e, i)}
                                            /></td>
                                            <td>
                                                <button type="button" className="btn btn-secondary"
                                                        onClick={() => this.removeTaskParameter(i)}>Remove
                                                </button>
                                            </td>
                                        </tr>)
                                })
                            }
                            </tbody>
                        </table>
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-secondary" data-dismiss="modal">Close
                        </button>
                        <button type="button" className="btn btn-primary"
                                onClick={this.saveSettings}>Save
                        </button>

                    </div>
                </div>
            </div>
        </div>);

    }

}

export default TaskSettings;