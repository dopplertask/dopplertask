package main

import (
	"github.com/dopplertask/doppler/cli/command"
)

func main() {

	rootCmd := command.NewCommand()

	rootCmd.Execute()

}
