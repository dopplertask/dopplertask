import * as React from "react";

class SaveModal extends React.Component {


    constructor(props) {
        super(props);
    }


    render() {
        return (<div className="modal fade" id="saveModal" tabIndex="-1" role="dialog"
                     aria-labelledby="saveModalLabel" aria-hidden="true">
            <div className="modal-dialog" role="document">
                <div className="modal-content">
                    <div className="modal-header">
                        <h5 className="modal-title" id="saveModalLabel">Save task</h5>
                        <button type="button" className="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    <div className="modal-body" id="saveModalBody">
                        <label
                            htmlFor="taskNameInput">Task name</label>
                        <input type="text" className="form-control" id="taskNameInput" value={this.props.taskName}
                               onChange={this.props.handleSaveModalField}/>
                        <label
                            htmlFor="descriptionInput">Description</label>

                        <input type="text" className="form-control" id="descriptionInput" onChange={this.props.handleSaveModalDescriptionField} value={this.props.description}/>
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-secondary" data-dismiss="modal"
                                onClick={this.props.closeSaveDialog}>Close
                        </button>
                        <button type="button" data-dismiss="modal" className="btn btn-primary"
                                onClick={this.props.saveWorkflow}>Save
                            changes
                        </button>
                    </div>
                </div>
            </div>
        </div>);

    }
}

export default SaveModal;