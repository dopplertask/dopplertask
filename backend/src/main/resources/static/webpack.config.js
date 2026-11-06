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
                { from: 'node_modules/@fontsource/plus-jakarta-sans/files/plus-jakarta-sans-latin-400-normal.woff2', to: 'fonts' },
                { from: 'node_modules/@fontsource/plus-jakarta-sans/files/plus-jakarta-sans-latin-500-normal.woff2', to: 'fonts' },
                { from: 'node_modules/@fontsource/plus-jakarta-sans/files/plus-jakarta-sans-latin-600-normal.woff2', to: 'fonts' },
                { from: 'node_modules/@fontsource/plus-jakarta-sans/files/plus-jakarta-sans-latin-700-normal.woff2', to: 'fonts' },
                { from: 'node_modules/@fontsource/geist-mono/files/geist-mono-latin-400-normal.woff2', to: 'fonts' },
                { from: 'node_modules/@fontsource/geist-mono/files/geist-mono-latin-500-normal.woff2', to: 'fonts' },
            ],
        }),
    ],
};
