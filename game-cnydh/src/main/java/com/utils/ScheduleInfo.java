package com.utils;

import java.util.concurrent.ScheduledFuture;

public class ScheduleInfo {

	private int taskid;
	private Runnable task;
	private long addTime;
	private long scheduleTime;
	private long schedulePeriod = 0;
	private ScheduledFuture<?> future;

	public ScheduleInfo(int taskid) {
		this.taskid = taskid;
	}

	public int getTaskid() {
		return taskid;
	}

	public Runnable getTask() {
		return task;
	}

	public void setTask(Runnable task) {
		this.task = task;
	}

	public long getAddTime() {
		return addTime;
	}

	public void setAddTime(long addTime) {
		this.addTime = addTime;
	}

	public long getScheduleTime() {
		return scheduleTime;
	}

	public void setScheduleTime(long scheduleTime) {
		this.scheduleTime = scheduleTime;
	}

	public long getSchedulePeriod() {
		return schedulePeriod;
	}

	public void setSchedulePeriod(long schedulePeriod) {
		this.schedulePeriod = schedulePeriod;
	}

	public ScheduledFuture<?> getFuture() {
		return future;
	}

	public void setFuture(ScheduledFuture<?> future) {
		this.future = future;
	}
	
	public boolean isCancelled() {
		return this.future.isCancelled() || this.future.isDone();
	}
}
