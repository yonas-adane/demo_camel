package com.example.demo;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.XMLRoutesDefinitionLoader;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

@SpringBootApplication
public class DemoApplication{

	public static String externalLibraryPath = "lib";
	public static String externalBeanName = "com.library.external.MyBean";

	public static void main(String[] args) {
		ApplicationContext applicationContext = SpringApplication.run(DemoApplication.class, args);

		SpringCamelContext springCamelContext = new SpringCamelContext(applicationContext);


		try {

			//load class from extrnal jar file
			Class<?> clazz = loadExternalJar(externalBeanName);

			//add route using java dsl - this works fine.
			addRouteJavaDsl(springCamelContext, clazz);

			//register bean - added this to see if it helps resolve the
			// java.lang.ClassNotFoundException error when using xml dsl.
			registerBean(clazz, externalBeanName, applicationContext);

			//bean is registered
			//add route using xml dsl - this part fails with the error message below
			/// Failed to create route myMercuryRoute at: >>>
			/// Bean[com.library.external.MyBean] <<<
			/// in route: Route(myMercuryRoute)[From[timer:foo] -> [Bean[com.library.e...
			/// because of java.lang.ClassNotFoundException: com.library.external.MyBean
			addRouteXMLDsl(applicationContext, springCamelContext);

		} catch (Exception ex) {
			ex.printStackTrace();
		}


	}

	private static void addRouteJavaDsl(SpringCamelContext springCamelContext, Class<?> clazz) {
		try {

			springCamelContext.addRoutes(new RouteBuilder() {
				@Override
				public void configure() {
					from("timer:foo")
							.routeId("myMarsRoute")
							.bean(clazz, "hello('Mars')") //passing clazz for bean type (i'd like to do the same in xml dsl)
							.log("Called hello(Mars)");
					//prints Hello mars. with timestamp
				}
			});

			springCamelContext.start();

			Thread.sleep(2_000);

			springCamelContext.stop();

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void addRouteXMLDsl(ApplicationContext applicationContext, SpringCamelContext springCamelContext) throws Exception{

		String routeXMLString = "<routes xmlns=\"http://camel.apache.org/schema/spring\">\n" +
				"    <route id=\"myMercuryRoute\">\n" +
				"        <from uri=\"timer:foo\"/>\n" +
				"        <bean beanType=\"com.library.external.MyBean\" method=\"hello('Mercury')\"/>\n" +
				"        <log message=\"Called hello(Mercury)\"/>\n" +
				"    </route>\n" +
				"</routes>";

		InputStream inputStream = new ByteArrayInputStream(routeXMLString.getBytes());

		CamelContext camelContext = new SpringCamelContext(applicationContext) ;

		ExtendedCamelContext extendedCamelContext = camelContext.adapt(ExtendedCamelContext.class);
		XMLRoutesDefinitionLoader xmlRoutesDefinitionLoader = extendedCamelContext.getXMLRoutesDefinitionLoader();
		RoutesDefinition routesDefinition = (RoutesDefinition) xmlRoutesDefinitionLoader.loadRoutesDefinition(camelContext, inputStream);

		if (springCamelContext.getApplicationContext().containsBeanDefinition(externalBeanName))
			System.out.printf("Bean found: " + externalBeanName);
		else
			System.out.printf("Bean not found: " + externalBeanName);

		//Bean found: com.library.external.MyBean

		try {

			springCamelContext.addRouteDefinitions(routesDefinition.getRoutes());

			List<Route> loadedRoutes = springCamelContext.getRoutes();

			for(Route route : loadedRoutes){
				System.out.printf(route.getId());
			}

		}
		catch(Exception ex){
			//Failed to create route myMercuryRoute at: >>>
			// Bean[com.library.external.MyBean] <<<
			// in route: Route(myMercuryRoute)[From[timer:foo] -> [Bean[com.library.e...
			// because of java.lang.ClassNotFoundException: com.library.external.MyBean
			ex.printStackTrace();
		}

	}

	private static Object getBean(String beanName, ApplicationContext applicationContext) {

		AutowireCapableBeanFactory factory = applicationContext.getAutowireCapableBeanFactory();

		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;

		if (registry.containsBeanDefinition(beanName)) {
			return registry.getBeanDefinition(beanName);
		}

		return null;

	}

	private static void invokeExternalMethod(Class<?> clazz) throws NoSuchMethodException, InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException {
		Constructor<?> myConstructor = clazz.getConstructor();
		Object myClassObj = myConstructor.newInstance();

		Method myMethod = myClassObj.getClass().getMethod("hello", String.class);

		Object result = myMethod.invoke(myClassObj, "Pluto");
	}

	private static Class<?> loadExternalJar(String className) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException {
		String jarFileName = "external.jar";

		File targetFile = new File(String.format("%s%s%s", externalLibraryPath, File.separator, jarFileName));

		URLClassLoader child = new URLClassLoader(
				new URL[]{targetFile.toURI().toURL()}
		);

		Class<?> myClass = Class.forName(className, true, child);

		return myClass;

	}

	private static void registerBean(Class<?> clazz, String beanName, ApplicationContext applicationContext) {

		AutowireCapableBeanFactory factory = applicationContext.getAutowireCapableBeanFactory();

		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;

		if (registry.containsBeanDefinition(beanName)) {
			registry.removeBeanDefinition(beanName);
		}

		BeanDefinitionBuilder beanDefinitionBuilder =
				BeanDefinitionBuilder.rootBeanDefinition(clazz);

		registry.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());

	}
}



