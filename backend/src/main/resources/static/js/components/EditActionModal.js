import * as React from "react";
import Editor from "./Editor";
import Pluralize from 'pluralize';
import {Tab, TabList, TabPanel, Tabs} from "react-tabs";

class EditActionModal extends React.Component {

    constructor(props) {
        super(props);
        this.renderFields = this.renderFields.bind(this);
        this.valueChange = this.valueChange.bind(this);
        this.editorValueChange = this.editorValueChange.bind(this);
        this.mapValueChange = this.mapValueChange.bind(this);
        this.addMap = this.addMap.bind(this);
        this.removeMapKey = this.removeMapKey.bind(this);
        this.getDropdownField = this.getDropdownField.bind(this);
        this.getStringField = this.getStringField.bind(this);
        this.getNumberField = this.getNumberField.bind(this);
        this.firstLetterCapitalAndPluralize = this.firstLetterCapitalAndPluralize.bind(this);
        this.state = {
            customData: this.props.selectedAction.userData.customData
        }

    }

    valueChange(event) {
        const target = event.target;
        let value = target.value;
        if (target.type == "checkbox") {
            value = target.checked;
        }

        this.props.updateFieldData(target.id, value);
    }

    editorValueChange(name, value) {

        this.props.updateFieldData(name, value)
    }

    /**
     * Adds a new row to the map or object
     *
     * @param mapName is the property
     * @param mapAttrObject to add to the map
     */
    addMap(mapName, mapAttrObject) {
        let mapObjects = this.props.selectedAction.userData.customData[mapName];
        if (mapObjects === undefined) {
            mapObjects = [];
        }

        this.props.updateFieldData(mapName, [
            ...mapObjects,
            mapAttrObject
        ]);
    }

    /**
     * Edits a field of a map or object
     *
     * @param name of the property
     * @param index of the current object in the property
     * @param attrName inside the current object
     * @param value to set for the attribute
     */
    mapValueChange(name, index, attrName, value) {
        this.props.selectedAction.userData.customData[name][index][attrName] = value;
    }

    /**
     * Removes key from an array
     *
     * @param name of the property
     * @param index to remove
     */
    removeMapKey(name, index) {
        this.props.selectedAction.userData.customData[name].splice(index, 1);
        this.forceUpdate();
    }

    firstLetterCapitalAndPluralize(phrase) {
        return Pluralize(phrase
            .toLowerCase()
            .split(' ')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1))
            .join(' '));
    }

    renderFields() {
        let propertiesField = {Properties: []};
        if (this.props.selectedAction.userData !== undefined && this.props.selectedAction.userData.propertyInformationList
            !== undefined) {
            // See if this is a trigger
            if (this.props.selectedAction.userData.customData.triggerSuffix != undefined && this.props.selectedAction.userData.name == "Webhook") {
                let triggerSuffix = <div className="form-group"><label
                    htmlFor="triggerSuffix">Webhook URL ({this.props.selectedAction.userData.customData.method})</label>
                    <div
                        style={{
                            wordWrap: "break-word",
                            fontSize: "small"
                        }}>{location.protocol}//{window.location.hostname}:{location.port}/webhook/{this.props.taskName}/{this.props.selectedAction.userData.name}/{this.props.selectedAction.userData.customData.triggerSuffix}/{this.props.selectedAction.userData.customData.path
                    != null
                    && this.props.selectedAction.userData.customData.path}</div>
                </div>;

                if (propertiesField["Properties"] == undefined) {
                    propertiesField["Properties"] = [];
                }
                propertiesField["Properties"].push(triggerSuffix)
            }

            this.props.selectedAction.userData.propertyInformationList.map(propertyInformation => {
                // Make sure that we have an array
                if (propertiesField[this.firstLetterCapitalAndPluralize(propertyInformation.category)] == undefined) {
                    propertiesField[this.firstLetterCapitalAndPluralize(propertyInformation.category)] = [];
                }

                let temp;
                let actionProperty = this.props.selectedAction.userData.customData[propertyInformation.name];
                switch (propertyInformation.type) {
                    case "STRING":
                        temp =
                            this.getStringField(propertyInformation,
                                actionProperty, true);
                        break;
                    case "MULTILINE":
                        temp =
                            this.getStringField(propertyInformation,
                                actionProperty, false);
                        break;
                    case "NUMBER":
                        temp = this.getNumberField(propertyInformation, actionProperty)
                        break;
                    case "BOOLEAN":
                        let booleanCheckedValue = propertyInformation.defaultValue === "true";
                        if (actionProperty !== undefined) {
                            booleanCheckedValue = actionProperty;
                        }
                        temp =
                            <div className="form-group" key={propertyInformation.name}><label
                                htmlFor={propertyInformation.name}>{propertyInformation.displayName}</label><small
                                className="form-text text-muted">{propertyInformation.description}</small> <input
                                type="checkbox"
                                id={propertyInformation.name}
                                checked={booleanCheckedValue
                                || false}
                                onChange={this.valueChange}/>

                            </div>;
                        break;
                    case "DROPDOWN":
                        temp =
                            this.getDropdownField(propertyInformation, actionProperty, true);
                        break;

                    case "MAP":
                        // Initialize Map if it does not exist.
                        if (actionProperty === undefined) {
                            actionProperty = []
                        }

                        // Construct object
                        let newMapObject = {};
                        propertyInformation.options.map(selectOption => {
                            newMapObject[selectOption.name] = selectOption.defaultValue || "";
                        });

                        temp =
                            <div className="form-group" key={propertyInformation.name}><label
                                htmlFor={propertyInformation.name}>{propertyInformation.displayName}</label> <a href="#"
                                                                                                                onClick={() => this.addMap(
                                                                                                                    propertyInformation.name,
                                                                                                                    newMapObject)}>Add</a>
                                <table className="table">
                                    <thead>
                                    <tr className="d-flex">
                                        {propertyInformation.options.map(selectOption => {
                                            return (<th className="col-sm" key={selectOption.name}
                                                        value={selectOption.name}>{selectOption.displayName}</th>)
                                        })}
                                        <th>Actions</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {
                                        actionProperty.map(
                                            (header, index) => (
                                                <tr key={header.id} className="d-flex">
                                                    {propertyInformation.options.map(selectOption => {
                                                        if (selectOption.type == "DROPDOWN") {
                                                            return (<td className="col-sm">
                                                                {this.getDropdownField(selectOption, header[selectOption.name],
                                                                    false,
                                                                    (value) => {
                                                                        this.mapValueChange(
                                                                            propertyInformation.name, index,
                                                                            selectOption.name,
                                                                            value.target.value)
                                                                    },
                                                                    header.id + "_" + header[selectOption.name]
                                                                    + "_"
                                                                    + selectOption.name
                                                                    + "_" + index)}
                                                            </td>)
                                                        } else {
                                                            return (
                                                                <td className="col-sm">
                                                                    <Editor
                                                                        key={header[selectOption.name] + "_" + selectOption.name
                                                                        + "_" + index + "_" + (this.props.selectedAction.userData.customData["scriptLanguage"].toLowerCase()
                                                                            || "velocity")} simple={true}
                                                                        id={header[selectOption.name] + "_" + selectOption.name
                                                                        + "_" + index}
                                                                        onChange={(value) => {
                                                                            this.mapValueChange(propertyInformation.name, index,
                                                                                selectOption.name, value)
                                                                        }}
                                                                        value={header[selectOption.name]
                                                                        || ""}
                                                                        scriptLanguage={this.props.selectedAction.userData.customData["scriptLanguage"].toLowerCase()
                                                                        || "velocity"}
                                                                        extraSuggestions={this.props.taskParameters}/>
                                                                </td>
                                                            )
                                                        }
                                                    })}
                                                    <td><a href="#" className="btn btn-danger"
                                                           onClick={() => this.removeMapKey(propertyInformation.name,
                                                               index)}>Delete</a></td>
                                                </tr>
                                            ))
                                    }
                                    </tbody>

                                </table>


                            </div>;
                        break;
                }


                propertiesField[this.firstLetterCapitalAndPluralize(propertyInformation.category)].push(temp);
            })
        }

        // Create tabs with the dynamic values from the properties
        let finalTabHeaders = [];
        let finalTabPanels = [];
        Object.entries(propertiesField).forEach((value, index) => {
            finalTabHeaders.push(<Tab>{value[0]}</Tab>);

            let properties = [];
            value[1].forEach(property => {
                properties.push(property)
            });

            finalTabPanels.push(<TabPanel>
                {properties}
            </TabPanel>);

        })


        return (
            <Tabs>
                <TabList>
                    {finalTabHeaders}
                </TabList>

                {finalTabPanels}
            </Tabs>
        );
    }

    /**
     * Constructs a dropdown select field.
     *
     * @param propertyInformation containing information about the field.
     * @param actionProperty is the property to modify.
     * @returns {*}
     */
    getDropdownField(propertyInformation, actionProperty, title, customCallBack, customName) {
        let callBack = this.valueChange;
        if (customCallBack !== undefined) {
            callBack = customCallBack;
        }

        let fieldName = propertyInformation.name;
        if (customName !== undefined) {
            fieldName = customName;
        }

        let includeTitle = title ? (<label
            htmlFor={fieldName}>{propertyInformation.displayName}</label>) : ("");
        return <div className="form-group" key={fieldName}>
            {includeTitle}
            <small className="form-text text-muted">{propertyInformation.description}</small>
            <select className="form-control" id={fieldName}
                    onChange={callBack}
                    value={actionProperty
                    || "VELOCITY"}>
                {propertyInformation.options.map(selectOption => {
                    return (<option key={selectOption.name}
                                    checked={actionProperty
                                    || propertyInformation.defaultValue || false}
                                    value={selectOption.name}>{selectOption.displayName}</option>)
                })}
            </select>

        </div>;
    }

    /**
     * Constructs a string field.
     *
     * @param propertyInformation containing information about the field.
     * @param actionProperty is the property to modify.
     * @param singleLine when set to true gives a single line field, otherwise multiline.
     * @returns {*}
     */
    getStringField(propertyInformation, actionProperty, singleLine) {
        return <div className="form-group"
                    key={propertyInformation.name + " " + (this.props.selectedAction.userData.customData["scriptLanguage"].toLowerCase()
                        || "velocity")}><label
            htmlFor={propertyInformation.name}>{propertyInformation.displayName}</label>
            <small className="form-text text-muted">{propertyInformation.description}</small>
            <Editor id={propertyInformation.name} onChange={(value) => {

                this.editorValueChange(propertyInformation.name, value)
            }}
                    simple={singleLine}
                    value={actionProperty || propertyInformation.defaultValue
                    || ""}
                    scriptLanguage={this.props.selectedAction.userData.customData["scriptLanguage"].toLowerCase()
                    || "velocity"} extraSuggestions={this.props.taskParameters}/>

        </div>;
    }

    /**
     * Constructs a number field.
     *
     * @param propertyInformation containing information about the field.
     * @param actionProperty is the property to modify.
     * @returns {*}
     */
    getNumberField(propertyInformation, actionProperty) {
        return <div className="form-group"
                    key={propertyInformation.name + " " + (this.props.selectedAction.userData.customData["scriptLanguage"].toLowerCase()
                        || "velocity")}><label
            htmlFor={propertyInformation.name}>{propertyInformation.displayName}</label>
            <small className="form-text text-muted">{propertyInformation.description}</small>
            <input
                className="form-control"
                type="number"
                id={propertyInformation.name}
                value={actionProperty
                || propertyInformation.defaultValue || ""}
                onChange={this.valueChange}/>

        </div>;
    }

    render() {

        return (<div className="modal fade" id="actionEditModal" tabIndex="-1" role="dialog"
                     aria-labelledby="actionEditModalLabel" aria-hidden="true">
            <div className="modal-dialog modal-xl" role="document">
                <div className="modal-content">
                    <div className="modal-header">
                        <h5 className="modal-title" id="actionEditModalLabel">Edit action</h5>
                        <button type="button" className="close" data-dismiss="modal" aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                    </div>
                    <div className="modal-body" id="actionEditModalBody">
                        <div className="container">
                            <div className="row">
                                <div className="col-8">
                                    {this.renderFields()}
                                </div>
                                <div className="col-4">
                                    Action execution result:
                                    <br/>
                                    <pre id="actionExecutionOutput"
                                         className="code-pre">{this.props.selectedAction.userData
                                    != undefined
                                    && this.props.selectedAction.userData.lastSingleActionExecutionOutput
                                    != undefined
                                    && this.props.selectedAction.userData.lastSingleActionExecutionOutput}</pre>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div className="modal-footer">
                        <button type="button" className="btn btn-primary" onClick={this.props.executeAction}>Execute
                            node
                        </button>

                    </div>
                </div>
            </div>
        </div>);

    }

}

export default EditActionModal;