const request = require('supertest');
const express = require('express');
const webhookRoutes = require('../src/routes/webhook');

const app = express();
app.use(express.json());
app.use('/', webhookRoutes);

describe('Webhook Route', () => {
  it('should handle task names containing forward slashes', async () => {
    const taskName = 'folder/subtask';
    const encodedTaskName = encodeURIComponent(taskName);
    const response = await request(app).post(`/webhook/${encodedTaskName}`);
    
    expect(response.status).toBe(200);
    expect(response.body.message).toContain(taskName);
  });
});