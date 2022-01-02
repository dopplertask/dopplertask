package command

import (
	"fmt"
	"path/filepath"

	"github.com/spf13/cobra"
)

type commonOptions struct {
	dopplerURL string
}

type runOptions struct {
	*commonOptions
	taskName      string
	parameters    map[string]string
	scheduledTask bool
}

type buildOptions struct {
	*commonOptions
	filePath     string
	absolutePath string
	fileName     string
	parameters   map[string]string
}

type processOptions struct {
	*commonOptions
	absolutePath string
}

type pushOptions struct {
	*commonOptions
	taskName string
}

type pullOptions struct {
	*commonOptions
	taskName string
}

type deleteTaskOptions struct {
	*commonOptions
	taskNameOrChecksum string
	forceDelete        bool
}

type deleteExecutionOptions struct {
	*commonOptions
	executionID string
}

type renameTaskOptions struct {
	*commonOptions
	sourceTaskName string
	targetTaskName string
}

type loginOptions struct {
	*commonOptions
	username string
	password string
}

// NewCommand creates a root command with `run, build`
func NewCommand() (rootCmd *cobra.Command) {

	var opts commonOptions

	rootCmd = &cobra.Command{Use: "doppler"}

	// Add flags to rootCmd
	flags := rootCmd.PersistentFlags()
	flags.StringVarP(&opts.dopplerURL, "url", "u", "http://localhost:8090/", "doppler backend url")

	rootCmd.AddCommand(defineRunCmd(&opts),
		defineBuildCmd(&opts),
		defineProcessCmd(&opts),
		definePushCmd(&opts),
		definePullCmd(&opts),
		defineTasksCmd(&opts),
		defineDeleteTaskCmd(&opts),
		defineDeleteExecutionCmd(&opts),
		defineRenameTaskCmd(&opts),
		defineLoginCmd(&opts))

	return
}

func defineRunCmd(copts *commonOptions) *cobra.Command {

	var opts runOptions

	cmd := &cobra.Command{
		Use:   "run [Task name]",
		Short: "run run task",
		Long:  `run is for running tasks`,
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			opts.taskName = args[0]
			opts.commonOptions = copts

			resp, err := runTask(&opts)

			if err != nil {
				fmt.Println("Task failed, error: ", err)
			} else {
				if opts.scheduledTask != true {
					subOpts := &subscribeOptions{
						uri:         "localhost:61613",
						queueName:   "/queue/taskexecution_destination",
						executionID: resp.ID,
					}
					err = consumeData(subOpts)
					if err != nil {
						fmt.Println("Task failed, error: ", err)
					}
				} else {
					fmt.Println(resp.ID)
				}
			}
		},
	}

	flags := cmd.Flags()
	flags.StringToStringVarP(&opts.parameters, "parameter", "p", map[string]string{}, "task parameters")
	flags.BoolVarP(&opts.scheduledTask, "scheduled_task", "d", false, "scheduled task")

	return cmd

}

func defineBuildCmd(copts *commonOptions) *cobra.Command {

	var opts buildOptions

	cmd := &cobra.Command{
		Use:   "build [PATH]",
		Short: "build builds a task",
		Long:  `build is for building a task`,
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			opts.filePath = args[0]
			opts.absolutePath, _ = filepath.Abs(opts.filePath)

			opts.commonOptions = copts

			resp, err := buildTask(&opts)
			if err != nil {
				fmt.Println("Task build failed, error: ", err)
			} else {
				fmt.Println("Task built with checksum: ", resp.Checksum)
			}
		},
	}

	flags := cmd.Flags()
	flags.StringToStringVarP(&opts.parameters, "parameter", "p", map[string]string{}, "task parameters")
	flags.StringVarP(&opts.fileName, "file", "f", "Dopplerfile", "filename")

	return cmd

}

func defineProcessCmd(copts *commonOptions) *cobra.Command {

	var opts processOptions

	cmd := &cobra.Command{
		Use:   "ps",
		Short: "ps shows all executions",
		Long:  `ps shows all executions, both started and finished ones`,
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts

			resp, err := getExecutions(&opts)
			if err != nil {
				fmt.Println("Get executions failed, error: ", err)
			} else {

				outputString := "%-*s  %-18s  %-10s  %-30s  %-18s"
				fmt.Println(fmt.Sprintf(outputString, 15, "Execution ID", "Task Name", "Status", "Start Date", "End Date"))

				for _, s := range resp.Executions {
					fmt.Println(fmt.Sprintf(outputString, 15, s.ExecutionID, s.TaskName, s.Status, s.StartDate, s.EndDate))
				}

			}
		},
	}

	return cmd

}

func definePushCmd(copts *commonOptions) *cobra.Command {

	var opts pushOptions

	cmd := &cobra.Command{
		Use:   "push [Task Name]",
		Short: "push [Task Name]",
		Long:  `push task to the global doppler repo`,
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts
			opts.taskName = args[0]

			resp, err := pushTask(&opts)
			if err != nil {
				fmt.Println("Task push failed, error: ", err)
			} else {
				fmt.Println("Tash push success: ", resp.TaskName)

			}
		},
	}

	return cmd

}

func definePullCmd(copts *commonOptions) *cobra.Command {

	var opts pullOptions

	cmd := &cobra.Command{
		Use:   "pull [Task Name]",
		Short: "pull [Task Name]",
		Long:  `pull task from the global doppler repo`,
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts
			opts.taskName = args[0]

			resp, err := pullTask(&opts)
			if err != nil {
				fmt.Println("Task pull failed, error: ", err)
			} else {
				fmt.Println("Tash pull success: ", resp.Checksum)

			}
		},
	}

	return cmd

}

func defineTasksCmd(copts *commonOptions) *cobra.Command {

	var opts processOptions

	cmd := &cobra.Command{
		Use:   "tasks",
		Short: "tasks shows all tasks",
		Long:  `tasks shows all tasks`,
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts

			resp, err := getTasks(&opts)
			if err != nil {
				fmt.Println("Get Tasks failed, error: ", err)
			} else {

				outputString := "%-18s  %-30s  %-30s"
				fmt.Println(fmt.Sprintf(outputString, "Task Name", "Created", "Checksum"))

				// Workaround , because taskId is an int, we could run strconv or this method
				outputString = "%-18s  %-30s  %-30s"
				for _, s := range *resp {

					fmt.Println(fmt.Sprintf(outputString, s.TaskName, s.Created, s.Checksum))
				}

			}
		},
	}

	return cmd

}

func defineDeleteTaskCmd(copts *commonOptions) *cobra.Command {

	var opts deleteTaskOptions

	cmd := &cobra.Command{
		Use:   "rmt [Task Name|Checksum]",
		Short: "rmt deletes task",
		Long:  `rmt deletes task from doppler`,
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts
			opts.taskNameOrChecksum = args[0]

			resp, err := deleteTask(&opts)
			if err != nil {
				fmt.Println("Delete task failed, error: ", err)
			} else {
				fmt.Println("Delete task success: ", resp.Message)

			}
		},
	}
	flags := cmd.Flags()
	flags.BoolVarP(&opts.forceDelete, "force_delete", "f", false, "force delete task")
	return cmd
}

func defineDeleteExecutionCmd(copts *commonOptions) *cobra.Command {

	var opts deleteExecutionOptions

	cmd := &cobra.Command{
		Use:   "rm [Execution ID]",
		Short: "rm deletes execution",
		Long:  `rm deletes execution from doppler`,
		Args:  cobra.ExactArgs(1),
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts
			opts.executionID = args[0]

			resp, err := deleteExecution(&opts)
			if err != nil {
				fmt.Println("Delete execution failed, error: ", err)
			} else {
				fmt.Println("Delete execution success: ", resp.ExecutionID, resp.TaskName, resp.Status, resp.StartDate, resp.EndDate)

			}
		},
	}

	return cmd
}

func defineRenameTaskCmd(copts *commonOptions) *cobra.Command {

	var opts renameTaskOptions

	cmd := &cobra.Command{
		Use:   "rename [SourceTaskName] [TargetTaskName]",
		Short: "rename renames task name",
		Long:  `rename changes task name from SourceTaskName to TargetTaskName`,
		Args:  cobra.ExactArgs(2),
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts
			opts.sourceTaskName = args[0]
			opts.targetTaskName = args[1]

			resp, err := renameTask(&opts)
			if err != nil {
				fmt.Println("Rename task failed, error: ", err)
			} else {
				fmt.Println("Rename task success: ", resp.TaskName, resp.TaskID, resp.Checksum, resp.Actions)

			}
		},
	}

	return cmd
}

func defineLoginCmd(copts *commonOptions) *cobra.Command {

	var opts loginOptions

	cmd := &cobra.Command{
		Use:   "login [username] [password]",
		Short: "login",
		Long:  `login with username and password`,
		Args:  cobra.ExactArgs(2),
		Run: func(cmd *cobra.Command, args []string) {
			opts.commonOptions = copts
			opts.username = args[0]
			opts.password = args[1]

			resp, err := login(&opts)
			if err != nil {
				fmt.Println("Login failed, error: ", err)
			} else {
				fmt.Println("Login success: ", resp.Message)

			}
		},
	}

	return cmd
}
