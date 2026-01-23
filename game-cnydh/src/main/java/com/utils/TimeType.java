package com.utils;

public enum TimeType {

	MILLISECONDS(1), 
	SECONDS(2), 
	MINUTES(3), 
	HOURS(4), 
	DAYS(5), 
	WEEKS(6), 
	MONTHS(7), 
	YEARS(8);

	private int value;

	TimeType(int value) {
		this.value = value;
	}

	public byte getValue() {
		return (byte) value;
	}
}
