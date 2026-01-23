package com.game.timer;

public class TimerFactory {

	private static ServiceTimer timer = new ServiceTimer();

	public static ServiceTimer getTimer() {
		return timer;
	}
}
