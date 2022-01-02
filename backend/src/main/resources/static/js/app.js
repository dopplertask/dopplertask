import "script-loader!./common/jquery.js";
import "script-loader!./common/jquery-ui.js";
import "script-loader!./common/jquery.browser.js";
import "script-loader!./common/jquery.layout.js";

import React from 'react';
import ReactDOM from 'react-dom';
import MainApp from "./components/MainApp";

import "script-loader!./common/draw2d.js";

import "script-loader!./common/Application.js";
import "script-loader!./common/HoverConnection.js";
import "script-loader!./common/Toolbar.js";
import "script-loader!./common/View.js";

import "script-loader!./common/MyInterceptorPolicy.js";

// Figures
import "script-loader!./common/StartFigure.js";
import "script-loader!./common/BetweenFigure.js";
import "script-loader!./common/IfAction.js";
import "script-loader!./common/SwitchAction.js";

import "script-loader!./common/bootstrap.min.js";

// Code editing tools
import "script-loader!./common/ace.js";
import "script-loader!./common/ext_language_tools.js";
import "script-loader!./common/mode-javascript.js";
import "script-loader!./common/mode-velocity.js";

import "script-loader!./common/stomp.min.js";

ReactDOM.render(
    <MainApp/>,
    document.getElementById('app_container')
);