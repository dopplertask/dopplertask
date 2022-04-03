function makeid(length) {
    var result = '';
    var characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    var charactersLength = characters.length;
    for (var i = 0; i < length; i++) {
        result += characters.charAt(Math.floor(Math.random() * charactersLength));
    }
    return result;
}


let IfAction = draw2d.shape.node.Between.extend({


    init: function (attr) {
        this._super(attr);
        this.setBackgroundColor("#FFF");
        this.setRadius(12);
        this.setResizeable(false);
        this.setWidth(80);
        this.setHeight(80);
        this.createPort("output");
        this.getPorts().each(function (i, port) {
            port.setName(port.getId())
        })
        this.getOutputPorts().each(function (i, port) {
            port.onConnect = function (connection) {
                let labelText = i == 0 ? "True" : "False";
                let label = new draw2d.shape.basic.Label({text: labelText, stroke: 0, fontColor: "#0d0d0d"});
                connection.add(label, new draw2d.layout.locator.ParallelMidpointLocator());
            }
        })
        console.log(this.getPorts());

        let labelActionTypeName = new draw2d.shape.basic.Label({text: this.userData.name || ""});
        labelActionTypeName.setStroke(0);
        labelActionTypeName.setBold(true);
        labelActionTypeName.setFontSize(13);

        let label = new draw2d.shape.basic.Label({text: this.userData.customData["actionName"] || ""});
        label.setStroke(0);

        this.add(labelActionTypeName, new draw2d.layout.locator.BottomLocator(this));
        this.add(label, new draw2d.layout.locator.ReallyBottomLocator(this));

        let actionIcon = new draw2d.shape.basic.Image({
                                                          path: 'images/actions/IfAction.png',
                                                          width: 32,
                                                          height: 32,
                                                          minWidth: 32,
                                                          minHeight: 20,
                                                          boundingBox: new draw2d.geo.Rectangle(0, 0, 32, 32),
                                                          className: 'activity-image'
                                                      });
        actionIcon.onDoubleClick = function () {
            this.getParent().onDoubleClick();
        }
        this.add(actionIcon, new draw2d.layout.locator.CenterLocator(this));
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
