package com.game.timer;


import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


@Slf4j
public class ServiceTimer extends ScheduledThreadPoolExecutor {

	private int taskid = 0;
	private final ConcurrentHashMap<Integer, ScheduleInfo> scheduleInfos = new ConcurrentHashMap<Integer, ScheduleInfo>();

	public ServiceTimer() {
		super(12); //SCHEDULE_POOL_SIZE
		scheduleAtFixedRate(this::purge, 5, 5, TimeUnit.MINUTES);
	}

	public ScheduledFuture<?> schedule(Runnable task, long delay) {
		try {
			long nowTime = System.currentTimeMillis();
			ScheduleInfo scheduleInfo = new ScheduleInfo(this.getTaskid());
			scheduleInfo.setAddTime(nowTime);
			scheduleInfo.setScheduleTime(nowTime + delay);
			scheduleInfo.setTask(task);
			ScheduledFuture<?> future = super.schedule(task, delay, TimeUnit.MILLISECONDS);
			scheduleInfo.setFuture(future);
			scheduleInfos.put(scheduleInfo.getTaskid(), scheduleInfo);
			return future;
		}catch (Exception e){
			log.info("线程异常创建异常");
		}
		return null;
	}

	public ScheduledFuture<?> schedule(Runnable task, Date time) {
		long nowTime = System.currentTimeMillis();
		long taskTime = time.getTime();
		ScheduleInfo scheduleInfo = new ScheduleInfo(this.getTaskid());
		scheduleInfo.setAddTime(nowTime);
		scheduleInfo.setScheduleTime(taskTime);
		scheduleInfo.setTask(task);
		ScheduledFuture<?> future = super.schedule(task, taskTime - nowTime, TimeUnit.MILLISECONDS);
		scheduleInfo.setFuture(future);
		scheduleInfos.put(scheduleInfo.getTaskid(), scheduleInfo);
		return future;
	}

	public ScheduledFuture<?> schedule(Runnable task, long delay, long period) {
		long nowTime = System.currentTimeMillis();
		ScheduleInfo scheduleInfo = new ScheduleInfo(this.getTaskid());
		scheduleInfo.setAddTime(nowTime);
		scheduleInfo.setScheduleTime(nowTime + delay);
		scheduleInfo.setSchedulePeriod(period);
		scheduleInfo.setTask(task);
		ScheduledFuture<?> future = super.scheduleAtFixedRate(task, delay, period, TimeUnit.MILLISECONDS);
		scheduleInfo.setFuture(future);
		scheduleInfos.put(scheduleInfo.getTaskid(), scheduleInfo);
		return future;
	}

	public ScheduledFuture<?> schedule(Runnable task, Date time, long period) {
		long nowTime = System.currentTimeMillis();
		long taskTime = time.getTime();
		ScheduleInfo scheduleInfo = new ScheduleInfo(this.getTaskid());
		scheduleInfo.setAddTime(nowTime);
		scheduleInfo.setScheduleTime(taskTime);
		scheduleInfo.setSchedulePeriod(period);
		scheduleInfo.setTask(task);
		ScheduledFuture<?> future = super.scheduleAtFixedRate(task, taskTime - nowTime, period, TimeUnit.MILLISECONDS);
		scheduleInfo.setFuture(future);
		scheduleInfos.put(scheduleInfo.getTaskid(), scheduleInfo);
		return future;
	}

	public ScheduledFuture<?> schedule(Runnable task, Date time, int period, TimeType type) {
		long milliseconds = this.getMilliseconds(time, period, type);
		if (milliseconds > 0) {
			return this.schedule(task, time, milliseconds);
		} else {
			return this.schedule(task, time);
		}
	}

	public ScheduledFuture<?> schedule(Runnable task, long delay, int period, TimeType type) {
		long milliseconds = this.getMilliseconds(new Date(System.currentTimeMillis() + delay), period, type);
		if (milliseconds > 0) {
			return this.schedule(task, delay, milliseconds);
		} else {
			return this.schedule(task, delay);
		}
	}
	@Override
	public void purge() {
		try {
			log.info("自动清理 定时任务",this.scheduleInfos.size());
			if(!this.scheduleInfos.isEmpty()) {
				List<ScheduleInfo> bak_scheduleInfos = new ArrayList<ScheduleInfo>(this.scheduleInfos.values());
				for (ScheduleInfo existScheduleInfo : bak_scheduleInfos)
					if (existScheduleInfo.isCancelled())
						this.scheduleInfos.remove(existScheduleInfo.getTaskid());
			}
		} catch (Exception e) {
		}
		super.purge();
	}


	private long getMilliseconds(Date time, int period, TimeType type) {
		long milliseconds = 0;
		if (type == TimeType.MILLISECONDS) {
			milliseconds = period;
		} else if (type == TimeType.SECONDS) {
			milliseconds = period * 1000l;
		} else if (type == TimeType.MINUTES) {
			milliseconds = period * 60 * 1000l;
		} else if (type == TimeType.HOURS) {
			milliseconds = period * 60 * 60 * 1000l;
		} else if (type == TimeType.DAYS) {
			milliseconds = period * 24 * 60 * 60 * 1000l;
		} else if (type == TimeType.WEEKS) {
			milliseconds = period * 7 * 24 * 60 * 60 * 1000l;
		} else if (type == TimeType.MONTHS) {
			milliseconds = period * 30 * 7 * 24 * 60 * 60 * 1000l;
		} else if (type == TimeType.YEARS) {
			milliseconds = period * 365 * 30 * 7 * 24 * 60 * 60 * 1000l;
		}
		return milliseconds;
	}

	private synchronized int getTaskid() {
		return this.taskid++;
	}

	public void printScheduleInfo() {
		try {
			List<ScheduleInfo> existScheduleInfos = new ArrayList<ScheduleInfo>();
			if(this.scheduleInfos.size()>0 && this.scheduleInfos.values()!=null)
				existScheduleInfos.addAll(this.scheduleInfos.values());
			for (ScheduleInfo existScheduleInfo : existScheduleInfos) {
				StringBuilder infoText = new StringBuilder();
				infoText.append(existScheduleInfo.getTaskid());
				infoText.append("|");
				infoText.append(existScheduleInfo.getTask().getClass().getName());
				infoText.append("|");
				infoText.append(new Date(existScheduleInfo.getAddTime()));
				infoText.append("|");
				infoText.append(new Date(existScheduleInfo.getScheduleTime()));
				infoText.append("|");
				infoText.append((int) (existScheduleInfo.getSchedulePeriod() / 1000));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
