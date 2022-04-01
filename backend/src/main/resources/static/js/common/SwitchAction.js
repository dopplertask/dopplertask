function makeid(length) {
    var result = '';
    var characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    var charactersLength = characters.length;
    for (var i = 0; i < length; i++) {
        result += characters.charAt(Math.floor(Math.random() * charactersLength));
    }
    return result;
}


let SwitchAction = draw2d.shape.node.Between.extend({


    init: function (attr) {
        this._super(attr);

        let currentSwitch = this;

        this.getOutputPorts().each(function (i, port) {
            currentSwitch.removePort(port);

        })

        this.getInputPorts().each(function (i, port) {
            port.setName(port.getId())
        })

        this.setBackgroundColor("#FFF");
        this.setRadius(12);
        this.setResizeable(false);
        this.setWidth(80);
        this.setHeight(80);

        let label = new draw2d.shape.basic.Label({text: this.userData.customData["actionName"] || "SwitchAction"});
        label.setStroke(0);
        this.add(label, new draw2d.layout.locator.BottomLocator(this));

        let actionIcon = new draw2d.shape.basic.Image({
                                                          path: 'images/actions/SwitchAction.png',
                                                          width: 32,
                                                          height: 32,
                                                          minWidth: 32,
                                                          minHeight: 32,
                                                          boundingBox: new draw2d.geo.Rectangle(0, 0, 32, 32),
                                                          className: 'activity-image'
                                                      });
        actionIcon.onDoubleClick = function () {
            this.getParent().onDoubleClick();
        }
        this.add(actionIcon, new draw2d.layout.locator.CenterLocator(this));
    },

    updatePorts: function () {
        let currentSwitch = this;
        this.getOutputPorts().each(function (i, port) {
            currentSwitch.removePort(port);
        })

        for (let i = 0; i < this.userData.customData["switchCases"].length; i++) {
            this.createPort("output");
        }
        this.getOutputPorts().each(function (i, port) {
            port.setName(port.getId())

            port.onConnect = function (connection) {
                let labelText = i + "";
                let label = new draw2d.shape.basic.Label({text: labelText, stroke: 0, fontColor: "#0d0d0d"});
                connection.add(label, new draw2d.layout.locator.ParallelMidpointLocator());
            }
        })

        this.layoutPorts();
    },

    updateFigureLabel: function () {
        let currentFigure = this;
        this.getChildren().each(function (i, e) {
            if (e instanceof draw2d.shape.basic.Label) {
                e.setText(currentFigure.userData.customData["actionName"]);
                currentFigure.repaint()
            }
        });
    },

    /**
     * @method
     * Called if the user drop this element onto the dropTarget.
     *
     * In this Example we create a "smart insert" of an existing connection.
     * COOL and fast network editing.
     *
     * @param {draw2d.Figure} dropTarget The drop target.
     * @param {Number} x the x coordinate of the drop
     * @param {Number} y the y coordinate of the drop
     * @param {Boolean} shiftKey true if the shift key has been pressed during this event
     * @param {Boolean} ctrlKey true if the ctrl key has been pressed during the event
     * @private
     **/
    onDrop: function (dropTarget, x, y, shiftKey, ctrlKey) {
        console.log("onDrop")
        // Activate a "smart insert" If the user drop this figure on connection
        //
        if (dropTarget instanceof draw2d.Connection) {
            let oldSource = dropTarget.getSource();
            let oldTarget = dropTarget.getTarget();

            let insertionSource = this.getOutputPort(0)
            let insertionTarget = this.getInputPort(0)

            // ensure that oldSource ---> insertionTarget.... insertionSource ------>oldTarget
            //
            if (oldSource instanceof draw2d.InputPort) {
                oldSource = dropTarget.getTarget();
                oldTarget = dropTarget.getSource();
            }

            let stack = this.getCanvas().getCommandStack();

            let cmd = new draw2d.command.CommandReconnect(dropTarget);
            cmd.setNewPorts(oldSource, insertionTarget);
            stack.execute(cmd);

            let additionalConnection = createConnection();
            cmd = new draw2d.command.CommandConnect(oldTarget, insertionSource);
            cmd.setConnection(additionalConnection);
            stack.execute(cmd);
        }
    }

});
