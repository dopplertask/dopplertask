const express = require('express');
const router = express.Router();
const webhookController = require('../controllers/webhookController');

// Use a catch-all parameter to handle task names containing slashes
router.post('/webhook/:taskName(*)', webhookController.handleWebhook);

module.exports = router;