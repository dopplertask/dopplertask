import * as React from "react";
import Editor from "./Editor";

class ImportTaskModal extends React.Component {

    constructor(props) {
        super(props);
    }

    componentDidMount() {

    }

    componentDidUpdate(prevProps, prevState, snapshot) {

    }

    render() {
        return (<div className="modal fade" id="importTaskModal" tabIndex="-1" role="dialog"
                     aria-labelledby="importTaskModalLabel" aria-hidden="true">
            <div className="modal-dialog modal-lg" role="document">
                <div className="modal-content">
                    <div className="modal-header">
                        <h5 className="modal-title" id="importTaskModalLabel">Run task</h5>
                        <button type="button" className="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    <div className="modal-body" id="importTaskModalBody">
                        <h4>Import task</h4>

                        <div className="form-group">
                            <Editor id="importTaskEditor" onChange={() => void (0)} value=""
                                    simple={false} scriptLanguage="JSON"/>
                        </div>

                    </div>

                    <div className="modal-footer">
                        <button type="button" className={"btn btn-primary"}
                                onClick={this.props.importTask}>Import task
                        </button>
                    </div>
                </div>
            </div>
        </div>);

    }

}

export default ImportTaskModal;