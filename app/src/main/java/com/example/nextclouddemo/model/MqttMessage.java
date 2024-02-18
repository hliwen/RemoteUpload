package com.example.nextclouddemo.model;

public class MqttMessage {
	public String message;
	public int reSendTimes;

	public MqttMessage(String message) {
		this.message = message;
	}
}
