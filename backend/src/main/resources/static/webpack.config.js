const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const CopyPlugin = require('copy-webpack-plugin');

module.exports = {
    mode: 'production',
    entry: { app: path.join(__dirname, 'js', 'app.js') },
    output: {
        publicPath: '',
        path: path.join(__dirname, 'public'),
        filename: '[name].bundle.js',
        clean: true,
    },
    plugins: [
        new HtmlWebpackPlugin({ template: './src/index.html', filename: 'index.html', scriptLoading: 'defer' }),
        new CopyPlugin({
            patterns: [
                { from: 'css/flow', to: 'css' },
                { from: 'js/flow/stomp.min.js', to: 'stomp.min.js' },
                { from: 'icon.png', to: 'icon.png' },
            ],
        }),
    ],
};
