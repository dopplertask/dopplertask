import * as React from "react";

class openTaskModal extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            tasks: {},
            saved: false
        }
    }

    componentDidMount() {
        this.loadTasks();
    }

    loadTasks() {
        $.ajax({
                   type: "GET",
                   url: "/task/grouped",
                   contentType: 'application/json',
                   success: success => {
                       this.setState({tasks: success})
                   },
                   dataType: "json"
               });
    }

    deleteTask(task) {
        let versions = this.state.tasks[task];
        if (!confirm("Delete " + task + " and its " + versions.length + " saved version(s)? This cannot be undone.")) {
            return;
        }

        // One at a time, so the last deletion can see that no versions remain and stop the task's triggers.
        versions.reduce((previous, version) => previous.then(() => $.ajax({type: "DELETE", url: "/task/" + version.checksum})), $.when())
            .then(() => this.props.taskDeleted(task), () => this.props.showNotification("Could not delete " + task + "."))
            .always(() => this.loadTasks());
    }

    componentDidUpdate(prevProps, prevState) {
        if (prevProps.saved != this.props.saved) {
            if (this.props.saved) {
                this.loadTasks();
            }
            this.setState({
                              saved: this.props.saved
                          })
        }

    }

    render() {
        return (<div className="modal fade" id="openTaskModal" tabIndex="-1" role="dialog"
                     aria-labelledby="openTaskModalLabel" aria-hidden="true">
            <div className="modal-dialog modal-lg" role="document">
                <div className="modal-content">
                    <div className="modal-header">
                        <h5 className="modal-title" id="openTaskModalLabel">Open task</h5>
                        <button type="button" className="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    <div className="modal-body" id="openTaskModalBody">
                        <table className="table table-hover">
                            <thead>
                            <tr>
                                <th scope="col">Task name</th>
                                <th scope="col">Version</th>
                                <th scope="col">Actions</th>
                            </tr>
                            </thead>
                            <tbody>

                            {
                                Object.keys(this.state.tasks).map(task => {
                                    return (<tr key={task}>
                                        <td>{task}</td>
                                        <td>
                                            <select id={task + "_versions"}>
                                                {/* The sv-SE locale formats local time as YYYY-MM-DD HH:mm:ss. */}
                                                {this.state.tasks[task].map((taskValue) => <option key={taskValue.checksum}
                                                                                                   value={taskValue.checksum}>{new Date(taskValue.created).toLocaleString("sv-SE")}{taskValue.description && " - " + taskValue.description}</option>)}
                                            </select>
                                        </td>
                                        <td><a href="#" className="btn btn-primary"
                                               onClick={() => {
                                                   $("#openTaskModal").modal("hide");
                                                   this.props.openTask($("#" + $.escapeSelector(task) + "_versions").val())
                                               }}>Open</a> <a href="#" className="btn btn-danger"
                                               onClick={() => this.deleteTask(task)}>Delete</a></td>
                                    </tr>)
                                })
                            }
                            </tbody>
                        </table>
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-secondary" data-dismiss="modal">Close
                        </button>

                    </div>
                </div>
            </div>
        </div>);

    }

}

export default openTaskModal;