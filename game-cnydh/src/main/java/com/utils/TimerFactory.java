package com.utils;

public class TimerFactory {

	private static ServiceTimer timer = new ServiceTimer();

	public static ServiceTimer getTimer() {
		return timer;
	}
}
