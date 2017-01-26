package com.rfitc.omega.orchestrator;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;

import com.google.common.collect.Lists;
import com.rfitc.omega.configuration.Config;
import com.rfitc.omega.configuration.Task;

public class Monitor
{
	private static final String CONFIG_FILE = "configuration.xml";

	private void start (String path) throws Exception
	{
		Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
		scheduler.start();

		Path dir = Paths.get(path);
		WatchService watcher = FileSystems.getDefault().newWatchService();
		WatchKey key = dir.register(watcher,
				StandardWatchEventKinds.ENTRY_CREATE,
				StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_DELETE);

		while (true)
		{
			try
			{
				try
				{
					key = watcher.take();
				}
				catch (InterruptedException x)
				{
					return;
				}

				for (WatchEvent<?> event : key.pollEvents())
				{
					WatchEvent.Kind<?> kind = event.kind();

					if (kind == StandardWatchEventKinds.OVERFLOW)
					{
						continue;
					}

					@SuppressWarnings("unchecked")
					WatchEvent<Path> ev = (WatchEvent<Path>) event;
					Path filename = ev.context();

					Path child = dir.resolve(filename);
					if (!FilenameUtils.isExtension(child.getFileName()
							.toString(), "jar"))
					{
						System.err.format("New file '%s' is not a jar file.%n",
								filename);
						continue;
					}

					if (kind == StandardWatchEventKinds.ENTRY_CREATE)
					{
						System.out.println("Loading..");
						this.load(child);
					}
					else if (kind == StandardWatchEventKinds.ENTRY_MODIFY)
					{
						System.out.println("Reloading...");
						this.unload(child);
						this.load(child);
					}
					else if (kind == StandardWatchEventKinds.ENTRY_DELETE)
					{
						System.out.println("Unloading...");
						this.unload(child);
					}

				}

				boolean valid = key.reset();
				if (!valid)
				{
					System.out.println("Module Directory no longer valid");
					break;
				}
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
				continue;
			}
		}
	}

	private boolean isFileRedable (File file)
	{
		while (true)
		{
			try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
					FileChannel channel = raf.getChannel();)
			{

				FileLock lock = channel.lock();

				lock.release();

				System.out.println("Lock Obtained successful");
				return true;
			}
			catch (IOException ex)
			{
				continue;
			}
		}
	}

	private boolean load (Path child)
	{
		File newJar = child.toFile();

		this.isFileRedable(newJar);

		try (JarFile jar = new JarFile(newJar);
				InputStream is = jar.getInputStream(jar
						.getEntry(Monitor.CONFIG_FILE));)
		{
			String content = IOUtils.toString(is, "UTF-8");

			Config config = Config.fromXML(content);

			this.scheduleJob(config,
					newJar);
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private boolean unload (Path child)
	{
		String filename = FilenameUtils.getName(child.getFileName().toString());
		try
		{
			if (this.unScheduleJob(filename))
			{
				System.out.println("Removed all jobs for " + filename);
			}
			else
			{
				System.out.println("Could not remove all jobs for " + filename);
			}
		}
		catch (SchedulerException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	private void scheduleJob (Config config, File newJar)
	{
		try
		{
			String filename = FilenameUtils.getName(newJar.getAbsolutePath());
			Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
			scheduler.standby();

			List<Task> tasks = config.getTasks();

			for (Task task : tasks)
			{
				// create JOB
				JobDetail jobDetail = newJob(OrchestratorJob.class)
						.usingJobData("jar", newJar.getAbsolutePath())
						.usingJobData("class", task.getClassName())
						.usingJobData("method", task.getMethodName())
						.withIdentity("Job" + task.getClassName(),
								"group" + filename).build();

				CronTrigger trigger = newTrigger()
						.withIdentity("trigger" + task.getClassName(),
								"group" + filename)
						.withSchedule(cronSchedule(task.getCronString()))
						.build();

				scheduler.scheduleJob(jobDetail, trigger);
			}
			// Start Scheduler
			scheduler.start();
		}
		catch (SchedulerException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private boolean unScheduleJob (String filename) throws SchedulerException
	{
		Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
		Set<JobKey> jobs = scheduler.getJobKeys(GroupMatcher
				.jobGroupContains("group" + filename));
		System.out.println("Number of jobs to be removed: " + jobs.size());
		return scheduler.deleteJobs(Lists.newArrayList(jobs));
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main (String[] args) throws Exception
	{
		String path = "/lab";
		Monitor m = new Monitor();
		m.start(path);
	}

}
