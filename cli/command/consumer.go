package command

import (
	"encoding/json"
	"fmt"

	"github.com/jjeffery/stomp"
)

// BroadcastResult BroadcastResult
type BroadcastResult struct {
	Output     string
	OutputType string
}

type subscribeOptions struct {
	uri         string
	queueName   string
	executionID string
}

func consumeData(opts *subscribeOptions) (err error) {

	conn, err := stomp.Dial("tcp", opts.uri)
	if err != nil {
		return
	}

	sub, err := conn.Subscribe(opts.queueName, stomp.AckClient, stomp.SubscribeOpt.Header("selector", "executionId="+opts.executionID))

	if err != nil {
		return
	}

	for {
		// Recive message from queue
		msg := <-sub.C
		if msg.Err != nil {
			return
		}

		messageBody := string(msg.Body)
		var result BroadcastResult
		// Remove quotes from string, ex: "test" -> test
		json.Unmarshal([]byte(messageBody), &result)

		switch result.OutputType {
		case "STRING":
			fmt.Println(string(result.Output))
			break
		default:
			fmt.Println(result.Output)
		}


		err = conn.Ack(msg)
		if err != nil {
			return
		}

		// Exit the loop if it is the last message
		if msg.Header.Get("lastMessage") == "true" {
			break
		}
	}

	err = sub.Unsubscribe()
	if err != nil {
		return
	}

	conn.Disconnect()

	return
}
