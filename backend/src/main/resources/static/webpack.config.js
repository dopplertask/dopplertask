const path = require("path");
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyPlugin = require('copy-webpack-plugin');

module.exports = {
    entry: {
        app: path.join(__dirname, "js", "app.js")
    },
    module: {
        rules: [
            {
                test: /\.(js|jsx)$/,
                exclude: /node_modules/,
                use: [
                     "babel-loader"
                ]
            },
            {
                test: /react\-tabs\.css$/i,
                use: [
                    "style-loader",
                    {
                        loader: "css-loader",
                    }
                ]
            }
        ]
    },
    resolve: {
        extensions: ['*', '.js', '.jsx']
    },

    output: {
        publicPath: "",
        path: path.join(__dirname, 'public'),
        filename: '[name].bundle.js'
    },
    plugins: [
        new webpack.HotModuleReplacementPlugin(),
        new HtmlWebpackPlugin({
            template: "./src/index.html",
            filename: "./index.html"
        }),
        new CopyPlugin({
            patterns: [
                {from: 'css', to: 'css'},
                {from: 'webfonts', to: 'webfonts'},
                {from: 'images', to: 'images'}
            ]
        })
    ],
    optimization: {
        minimize: true,
        splitChunks: {
            chunks: 'all',
        }
    },
    devServer: {
        hot: true,
        historyApiFallback: true
    },
    externals: {
        // require("jquery") is external and available
        //  on the global var jQuery
        "jquery": "jQuery"
    }
};