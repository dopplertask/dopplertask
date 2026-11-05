exports.handleWebhook = (req, res) => {
  // Decode the task name parameter to handle encoded slashes or special characters
  const taskName = decodeURIComponent(req.params.taskName);
  
  if (!taskName) {
    return res.status(400).json({ error: 'Task name is required' });
  }

  // Logic to process the webhook for the specific task
  console.log(`Processing webhook for task: ${taskName}`);
  
  res.status(200).json({ message: `Webhook received for ${taskName}` });
};