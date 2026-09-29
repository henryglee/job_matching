package com.jobmatching.demo;

import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class JobMatchingApplication {

	public static void main(String[] args) {
		// Every timestamp column is "timestamp without time zone" and the app's own expiry and cleanup
		// cutoffs are UTC, but Hibernate's created/updated stamps (and LocalDateTime.now()) use the JVM's
		// zone. Pin it to UTC so they all agree, whatever zone the server is set to.
		TimeZone previous = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		if (!"UTC".equals(previous.getID()) && !"Etc/UTC".equals(previous.getID()) && !"GMT".equals(previous.getID())) {
			LoggerFactory.getLogger(JobMatchingApplication.class).warn(
					"Server time zone was {}; using UTC. Timestamps already stored while it ran in {} were written in that zone.",
					previous.getID(), previous.getID());
		}
		SpringApplication.run(JobMatchingApplication.class, args);
	}

}
