package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.RunFiles;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.io.ReadFileAction;
import com.dopplertask.dopplertask.domain.action.io.WriteFileAction;
import com.dopplertask.dopplertask.service.TaskService;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Files;
import java.nio.file.Path;

/** Read file keeps a file in the run, and Write file writes text or such a file. */
@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("test")
public class FileStepsTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private VariableExtractorUtil variables;

    @Test
    public void testAFileReadIsKeptAndCanBeWrittenElsewhereSuccess() throws Exception {
        Path folder = Files.createTempDirectory("file-steps");
        byte[] picture = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, (byte) 0xFF};
        Files.write(folder.resolve("logo.png"), picture);
        TaskExecution run = new TaskExecution();

        ActionResult read = read(run, folder.resolve("logo.png").toString(), "");
        Assert.assertEquals("logo.png", read.getOutputVariables().get("file"));
        Assert.assertEquals("image/png", read.getOutputVariables().get("mimeType"));
        Assert.assertArrayEquals(picture, RunFiles.INSTANCE.read(run, "logo.png"));

        WriteFileAction copy = writer(folder.resolve("copy.png").toString(), WriteFileAction.REPLACE);
        copy.setWrite(WriteFileAction.FILE);
        copy.setFile("logo.png");
        Assert.assertEquals(StatusCode.SUCCESS, copy.run(taskService, run, variables, null).getStatusCode());
        Assert.assertArrayEquals(picture, Files.readAllBytes(folder.resolve("copy.png")));
    }

    @Test
    public void testATextFileGivesItsTextSuccess() throws Exception {
        Path note = Files.createTempFile("note", ".txt");
        Files.writeString(note, "hello there");
        TaskExecution run = new TaskExecution();
        Assert.assertEquals("hello there", read(run, note.toString(), "greeting").getOutput());
        Assert.assertNotNull(RunFiles.INSTANCE.read(run, "greeting"));
    }

    @Test
    public void testWritingReplacesAddsToOrStopsAtAnExistingFileSuccess() throws Exception {
        Path file = Files.createTempFile("write", ".txt");
        TaskExecution run = new TaskExecution();
        Assert.assertEquals(StatusCode.SUCCESS, text(file, "a long first entry", WriteFileAction.REPLACE).run(taskService, run, variables, null).getStatusCode());
        text(file, "short", WriteFileAction.REPLACE).run(taskService, run, variables, null);
        Assert.assertEquals("short", Files.readString(file));
        text(file, " and more", WriteFileAction.APPEND).run(taskService, run, variables, null);
        Assert.assertEquals("short and more", Files.readString(file));
        ActionResult stopped = text(file, "x", WriteFileAction.STOP).run(taskService, run, variables, null);
        Assert.assertEquals(StatusCode.FAILURE, stopped.getStatusCode());
        Assert.assertEquals("short and more", Files.readString(file));
    }

    @Test
    public void testWritingAFileTheRunDoesNotHaveExplainsWhyFail() throws Exception {
        WriteFileAction write = writer(Files.createTempFile("missing", ".bin").toString(), WriteFileAction.REPLACE);
        write.setWrite(WriteFileAction.FILE);
        write.setFile("invoice.pdf");
        Assert.assertTrue(write.run(taskService, new TaskExecution(), variables, null).getErrorMsg().contains("no file called \"invoice.pdf\""));
    }

    private ActionResult read(TaskExecution run, String path, String keepAs) throws Exception {
        ReadFileAction read = new ReadFileAction();
        read.setFilename(path);
        read.setKeepAs(keepAs);
        return read.run(taskService, run, variables, null);
    }

    private static WriteFileAction text(Path file, String contents, String ifExists) {
        WriteFileAction write = writer(file.toString(), ifExists);
        write.setContents(contents);
        return write;
    }

    private static WriteFileAction writer(String path, String ifExists) {
        WriteFileAction write = new WriteFileAction();
        write.setFilename(path);
        write.setIfExists(ifExists);
        return write;
    }
}
