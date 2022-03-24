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
        let openTaskModal = this;
        $.ajax({
                   type: "GET",
                   url: "/task/grouped",
                   contentType: 'application/json',
                   success: success => {
                       openTaskModal.setState({tasks: success})
                   },
                   dataType: "json"
               });
    }

    componentDidUpdate(prevProps, prevState) {
        if (prevProps.saved != this.props.saved) {
            if (this.props.saved) {
                let openTaskModal = this;
                $.ajax({
                           type: "GET",
                           url: "/task/grouped",
                           contentType: 'application/json',
                           success: success => {
                               openTaskModal.setState({tasks: success})
                           },
                           dataType: "json"
                       });

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
                                                {this.state.tasks[task].map((taskValue) => <option key={taskValue.checksum}
                                                                                                   value={taskValue.checksum}>{taskValue.created}</option>)}
                                            </select>
                                        </td>
                                        <td><a href="#" className="btn btn-primary"
                                               onClick={() => {
                                                   $("#openTaskModal").modal("hide");
                                                   this.props.openTask($("#" + $.escapeSelector(task) + "_versions").val())
                                               }}>Open</a></td>
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