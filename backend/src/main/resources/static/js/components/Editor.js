import * as React from "react";

class Editor extends React.Component {
    constructor(props) {
        super(props);
        this.valueChange = this.valueChange.bind(this);
        this.containerId = `editor_${props.id}`
        this.scriptLanguage = this.props.scriptLanguage;
    }

    valueChange(value) {
        this.props.onChange(value)
    }

    componentDidMount() {

        console.log("Value: " + this.props.value);
        ace.config.set("basePath", "/");
        this.editor = window.ace.edit(this.containerId);

        this.editor.$blockScrolling = Infinity; // option suggested in the log by Ace Editor to disable the returning warning

        this.editor.session.setMode("ace/mode/" + this.scriptLanguage);

        this.editor.setAutoScrollEditorIntoView(true);
        this.editor.setShowPrintMargin(false);
        this.editor.setFontSize(16);

        this.editor.setOptions({
                                   enableBasicAutocompletion: true,
                                   enableSnippets: true,
                                   enableLiveAutocompletion: true
                               });

        this.editor.setOption("enableSnippets", true);
        this.editor.setOption("maxLines", this.props.simple ? 1 : 30);
        this.editor.setOption("minLines", this.props.simple ? 1 : 5);



        if(this.props.readOnly) {
            this.editor.setReadOnly(true);
        }

        if(this.props.extended) {
            this.editor.setOption("minLines", 30);
            this.editor.setOption("maxLines", 60);
        }
        let language = this.props.scriptLanguage;
        // At the moment, only taskParameters are handled.
        let extraSuggestions = this.props.extraSuggestions;
        var staticWordCompleter = {
            getCompletions: function(editor, session, pos, prefix, callback) {
                var wordList = ["Standard"];
                if(language == "velocity") {
                    wordList = ["$parameters", "$lastLog.output", "$executionId", "$logs"];
                    extraSuggestions.map((value) => {
                        wordList.push("$parameters.get(\"" + value["name"] + "\")");
                    });

                } else if(language == "javascript")  {
                    wordList = ["parameters", "lastLog.getOutput()", "executionId", "logs"];
                    extraSuggestions.map((value) => {
                        wordList.push("parameters.get(\"" + value["name"] + "\")");
                    });
                }
                callback(null, wordList.map(function(word) {
                    return {
                        caption: word,
                        value: word,
                        meta: "Default"
                    };
                }));

            }
        }
        this.editor.completers = [staticWordCompleter]


        this.editor.setValue(this.props.value, -1);

        this.editor.getSession().on("change", () => this.valueChange(this.editor.getValue()));


    }

    render() {
        return <div id={this.containerId} style={{
            margin: "0.5em",
            marginLeft: 0,
            border: "1px solid lightgrey", width: "100%"
        }}/>
    }
}

export default Editor
