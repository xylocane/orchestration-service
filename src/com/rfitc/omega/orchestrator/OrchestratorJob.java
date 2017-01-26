package com.rfitc.omega.orchestrator;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.xeustechnologies.jcl.JarClassLoader;
import org.xeustechnologies.jcl.JclObjectFactory;

public class OrchestratorJob implements Job
{

	@Override
	public void execute (JobExecutionContext context)
			throws JobExecutionException
	{

		String jarString = context.getJobDetail().getJobDataMap()
				.getString("jar");
		File jarFile = new File(jarString);

		String classURL = context.getJobDetail().getJobDataMap()
				.getString("class");
		
		String methodString = context.getJobDetail().getJobDataMap()
				.getString("method");

		try
		{
			JarClassLoader jcl = new JarClassLoader();
			URL jarURL = jarFile.toURI().toURL();
			
			jcl.add(jarURL);
			
			JclObjectFactory factory = JclObjectFactory.getInstance();
			
			Object bean = factory.create(jcl, classURL);
			Method method = bean.getClass().getMethod(methodString);
			method.invoke(bean);
			
			jcl.unloadClass(classURL);

		}
		catch (IllegalAccessException | NoSuchMethodException
				| SecurityException | IllegalArgumentException
				| InvocationTargetException | MalformedURLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
