Run JUnit tests remotely
==============

This is a JUnit extension which allows you to run any JUnit test remotely.

It supports any existing runner, and will fall back to running tests locally if the remote test server is not running.

Why would you want to do this?
------------------------------
Primary reason: If you're using [JRebel](http://zeroturnaround.com/jrebel/) you are probably used to being able to get code changes into your running server fast. But what about running tests?

Starting up a remote JUnit server with JRebel enabled, you'll get all the benefits of using JRebel also when writing tests. Of course, this is mostly relevant when writing
integration tests, for example in Spring, which need to boot up an application context to run the test.

Setting up:
-----------

- Add the required jar files to your project
- Add @RunWith(RemoteTestRunner.class) to your test classes.  The annotation can be placed on a superclass.
- Optionally @Remote to specify the location of the remote server as well as the test runner to use.
- Start the remote server. This can be done from an Ant script - this script assumes that the init target sets up the classpath properly:
	<target name="runtest" depends="init">
		<java classname="com.tradeshift.test.remote.RemoteServer" classpathref="classpath" fork="true">
			<classpath>
				<path refid="classpath" />
				<pathelement location="${basedir}/target/test-classes" />
				<pathelement location="${basedir}/target/classes" />
			</classpath>
		</java>
		
	</target>

- Run any test from Maven, Eclipse or whatever you are used to.

To use with JRebel, simply add the JRebel agent as a <jvmarg>. Here's a complete example:

	<target name="runtest" depends="init">
		<property environment="env"/>
		
		<fail message="JREBEL_HOME must be set">
			<condition><not><resourceexists><file file="${env.JREBEL_HOME}/jrebel.jar" /></resourceexists></not></condition>
		</fail>
		
		<artifact:dependencies pathId="classpath" useScope="test">
			<pom file="${basedir}/pom.xml" />
		</artifact:dependencies>
        <artifact:mvn fork="true" pom="${basedir}/pom.xml">
            <arg value="test-compile" />
        </artifact:mvn>
		<echo file="target/test-classes/rebel.xml"><![CDATA[
			<?xml version="1.0" encoding="UTF-8"?>
			<application xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.zeroturnaround.com" xsi:schemaLocation="http://www.zeroturnaround.com http://www.zeroturnaround.com/alderaan/rebel-2_0.xsd">
	
			    <classpath fallback="default">
			        <dir name="${basedir}/target/test-classes">
			        </dir>
			        <dir name="${basedir}/target/classes">
			        </dir>
			    </classpath>
	
			    <web>
			        <link target="/">
			            <dir name="${basedir}/src/main/webapp">
			            </dir>
			        </link>
			    </web>
	
			</application>
	]]>
		</echo>
		<java classname="com.tradeshift.test.remote.RemoteServer" classpathref="classpath" fork="true">
			<classpath>
				<path refid="classpath" />
				<pathelement location="${basedir}/target/test-classes" />
				<pathelement location="${basedir}/target/classes" />
			</classpath>
			<jvmarg value="-Dconfig.location=file:${configurationLocation}"/>
			<jvmarg value="-XX:MaxPermSize=512m" />
			<jvmarg value="-Xmx1024m" />
			<jvmarg value="-javaagent:${env.JREBEL_HOME}/jrebel.jar" />
		</java>
		
	</target>
	<target name="init">
		<mkdir dir="${basedir}/target" />
		<available file="${basedir}/lib/maven-ant-tasks-2.1.3.jar" property="tasks-exists" />
		<antcall target="-download" />
	
		<path id="maven-ant-tasks.classpath" path="${basedir}/lib/maven-ant-tasks-2.1.3.jar" />
		<typedef resource="org/apache/maven/artifact/ant/antlib.xml" uri="antlib:org.apache.maven.artifact.ant" classpathref="maven-ant-tasks.classpath" />
	</target>

